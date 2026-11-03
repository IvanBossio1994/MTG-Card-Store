package com.tcg.bot.controller;

import com.tcg.bot.model.Game;
import com.tcg.bot.model.GameCard;
import com.tcg.bot.model.GamePrint;
import com.tcg.bot.service.InventoryService;
import com.tcg.bot.service.games.PokemonCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.storage-dir=target/test-data-games")
@AutoConfigureMockMvc
class GamePageTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    InventoryService inventoryService;

    @MockitoBean
    PokemonCatalog pokemonCatalog;

    @Test
    void showsSearchResultsWithStockAndTheGamesInventory() throws Exception {
        when(pokemonCatalog.game()).thenReturn(Game.POKEMON);
        GameCard card = new GameCard();
        card.setRowIndex(2);
        card.setId("G1");
        card.setCatalogId("base1-4:holofoil");
        card.setName("Charizard");
        card.setSetName("Base");
        card.setNumber("4");
        card.setRarity("Rare Holo");
        card.setFinish("Holo");
        card.setQuantity(2);
        card.setLocalPrice(420000);
        when(inventoryService.getGameCards(Game.POKEMON)).thenReturn(List.of(card));
        when(pokemonCatalog.search("charizard")).thenReturn(List.of(new GamePrint("base1-4:holofoil", "Charizard",
                "Base", "BS", "4", "Rare Holo", "Holo", "", 350.0, "1999/01/09")));

        mvc.perform(get("/juegos/pokemon").param("q", "charizard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2 en stock")))
                .andExpect(content().string(containsString("Vender 1")))
                .andExpect(content().string(containsString("$ 420.000")))
                .andExpect(content().string(containsString("Inventario Pokemon")));
    }
}
