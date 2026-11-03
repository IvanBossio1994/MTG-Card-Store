package com.tcg.bot.service.games;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameCatalogTest {

    @Test
    void pokemonSplitsEachTcgplayerFinishIntoItsOwnPrinting() throws Exception {
        List<String> urls = new ArrayList<>();
        var catalog = new PokemonCatalog(url -> {
            urls.add(url);
            return """
                    {"data":[{"id":"base1-4","name":"Charizard","number":"4","rarity":"Rare Holo",
                      "set":{"id":"base1","name":"Base","ptcgoCode":"BS","releaseDate":"1999/01/09"},
                      "images":{"small":"https://img/base1-4.png"},
                      "tcgplayer":{"prices":{"holofoil":{"low":200.0,"mid":300.0,"market":350.5},
                                             "1stEditionHolofoil":{"mid":5000.0}}}},
                     {"id":"sv3-1","name":"Charmander","number":"1","rarity":"Common","set":{"id":"sv3","name":"Obsidian Flames"}}]}
                    """;
        });

        var prints = catalog.search("Charizard");

        assertThat(urls.get(0)).contains("q=name%3ACharizard*");
        assertThat(prints).hasSize(3);
        assertThat(prints.get(0).catalogId()).isEqualTo("base1-4:holofoil");
        assertThat(prints.get(0).finish()).isEqualTo("Holo");
        assertThat(prints.get(0).usd()).isEqualTo(350.5);
        assertThat(prints.get(0).setCode()).isEqualTo("BS");
        assertThat(prints.get(1).usd()).isEqualTo(5000.0);
        assertThat(prints.get(2).usd()).isNull();
        assertThat(prints.get(2).setCode()).isEqualTo("SV3");
    }

    @Test
    void yugiohSplitsEachSetAndRarityIntoItsOwnPrinting() throws Exception {
        var catalog = new YugiohCatalog(url -> """
                {"data":[{"id":46986414,"name":"Dark Magician",
                  "card_sets":[{"set_name":"Legend of Blue Eyes White Dragon","set_code":"LOB-EN005","set_rarity":"Ultra Rare","set_rarity_code":"(UR)","set_price":"45.10"},
                               {"set_name":"Starter Deck: Yugi","set_code":"SDY-006","set_rarity":"Ultra Rare","set_rarity_code":"(UR)","set_price":"0"}],
                  "card_images":[{"image_url_small":"https://img/dm.jpg"}]}]}
                """);

        var prints = catalog.search("dark magician");

        assertThat(prints).hasSize(2);
        assertThat(prints.get(0).catalogId()).isEqualTo("LOB-EN005:UR");
        assertThat(prints.get(0).setCode()).isEqualTo("LOB");
        assertThat(prints.get(0).number()).isEqualTo("EN005");
        assertThat(prints.get(0).usd()).isEqualTo(45.10);
        assertThat(prints.get(1).usd()).isNull();
    }

    @Test
    void noResultsIsAnEmptyList() throws Exception {
        assertThat(new YugiohCatalog(url -> null).search("zzz")).isEmpty();
        assertThat(new PokemonCatalog(url -> null).search("zzz")).isEmpty();
    }
}
