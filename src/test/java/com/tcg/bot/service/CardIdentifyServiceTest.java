package com.tcg.bot.service;

import com.tcg.bot.model.InventoryCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CardIdentifyServiceTest {

    private static final String DICTATE = """
            {"object":"list","has_more":false,"data":[
              {"name":"Dictate of Erebos","set":"jou","set_name":"Journey into Nyx","collector_number":"65",
               "released_at":"2014-05-02","finishes":["nonfoil","foil"],"image_uris":{"small":"https://img/jou.jpg"}},
              {"name":"Dictate of Erebos","flavor_name":"Elli's Rage","set":"sld","set_name":"Secret Lair Drop",
               "collector_number":"1563","released_at":"2024-03-01","finishes":["foil"],"image_uris":{"small":"https://img/sld.jpg"}}
            ]}""";

    static CardIdentifyService service(List<String> urls, String... responses) {
        List<String> queue = new ArrayList<>(List.of(responses));
        return new CardIdentifyService(url -> {
            urls.add(url);
            String next = queue.isEmpty() ? null : queue.remove(0);
            return "404".equals(next) ? null : next;
        });
    }

    @Test
    void findsCardByItsAlternateNameAndPutsThatPrintingFirst() throws Exception {
        List<String> urls = new ArrayList<>();
        var candidates = service(urls, DICTATE).candidates("ellis rage");

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).setCode()).isEqualTo("SLD");
        assertThat(candidates.get(0).name()).isEqualTo("Dictate of Erebos");
        assertThat(candidates.get(0).alternateName()).isEqualTo("Elli's Rage");
        assertThat(candidates.get(0).matchedByAlternateName()).isTrue();
        assertThat(candidates.get(0).onlyFinish()).isEqualTo("foil");
        assertThat(candidates.get(1).onlyFinish()).isEmpty();
    }

    @Test
    void realNameShowsEveryPrintingIncludingSpecialOnes() throws Exception {
        var candidates = service(new ArrayList<>(), DICTATE).candidates("Dictate of Erebos");

        assertThat(candidates).extracting(CardIdentifyService.Candidate::setCode).containsExactly("JOU", "SLD");
        assertThat(candidates.get(1).alternateName()).isEqualTo("Elli's Rage");
    }

    @Test
    void typoFallsBackToFuzzySearch() throws Exception {
        List<String> urls = new ArrayList<>();
        var candidates = service(urls, "404", "404", "{\"name\":\"Dictate of Erebos\"}", DICTATE)
                .candidates("dictate of erebus");

        assertThat(candidates).hasSize(2);
        assertThat(urls.get(1)).contains("lang%3Aany");
        assertThat(urls.get(2)).contains("/cards/named?fuzzy=");
        assertThat(urls.get(3)).contains("%21%22Dictate+of+Erebos%22");
    }

    @Test
    void onlyCardsMissingSetOrNumberNeedIdentification() {
        InventoryCard complete = new InventoryCard();
        complete.setName("Sol Ring");
        complete.setSetCode("CMM");
        complete.setCollectorNumber("410");
        InventoryCard missing = new InventoryCard();
        missing.setName("Sol Ring");

        assertThat(CardIdentifyService.needsIdentification(complete)).isFalse();
        assertThat(CardIdentifyService.needsIdentification(missing)).isTrue();
        assertThat(CardIdentifyService.normalize("Elli’s Rage")).isEqualTo("ellis rage");
    }
}
