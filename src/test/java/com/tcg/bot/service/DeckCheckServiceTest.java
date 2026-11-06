package com.tcg.bot.service;

import com.tcg.bot.model.InventoryCard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeckCheckServiceTest {

    private StockOperationsServiceTest.FakeStore store;
    private DeckCheckService service;

    @BeforeEach
    void setUp() {
        store = new StockOperationsServiceTest.FakeStore();
        store.cards.add(card("sol-cmm", 2, "Sol Ring", "CMM", "410", 1, "3000"));
        store.cards.add(card("sol-c21", 3, "Sol Ring", "C21", "263", 2, "1500"));
        store.cards.add(card("bolt", 4, "Lightning Bolt", "2XM", "117", 4, "2000"));
        store.cards.add(card("fable", 5, "Fable of the Mirror-Breaker // Reflection of Kiki-Jikki", "NEO", "141", 1, "20000"));
        store.cards.add(card("empty", 6, "Counterspell", "MH2", "267", 0, "1000"));
        service = new DeckCheckService(store);
    }

    @Test
    void findsWhatIsInStockAndWhatIsMissing() throws Exception {
        var result = service.check("""
                Deck
                4 Lightning Bolt
                2 Counterspell
                1 Fable of the Mirror-Breaker
                Creatures (20)
                Sideboard
                1 Fatal Push
                """);

        assertThat(result.lines()).extracting(DeckCheckService.Line::name)
                .containsExactly("Lightning Bolt", "Counterspell", "Fable of the Mirror-Breaker", "Fatal Push");
        assertThat(result.wanted()).isEqualTo(8);
        assertThat(result.found()).isEqualTo(5);
        assertThat(result.total()).isEqualTo(4 * 2000 + 20000);
        assertThat(result.complete()).hasSize(2);
        assertThat(result.absent()).hasSize(2);
        assertThat(result.missingText()).isEqualTo("2 Counterspell\n1 Fatal Push");
    }

    @Test
    void prefersTheRequestedEditionThenTheCheapest() throws Exception {
        var asked = service.check("1 Sol Ring (CMM) 410").lines().get(0);
        assertThat(asked.picks()).extracting(pick -> pick.card().getId()).containsExactly("sol-cmm");

        var any = service.check("3 Sol Ring").lines().get(0);
        assertThat(any.picks()).extracting(pick -> pick.card().getId()).containsExactly("sol-c21", "sol-cmm");
        assertThat(any.found()).isEqualTo(3);
        assertThat(any.subtotal()).isEqualTo(2 * 1500 + 3000);
    }

    @Test
    void doesNotUseTheSameCopyTwice() throws Exception {
        var result = service.check("2 Sol Ring\n2 Sol Ring (CMM)");

        assertThat(result.found()).isEqualTo(3);
        assertThat(result.wanted()).isEqualTo(4);
    }

    private static InventoryCard card(String id, int row, String name, String set, String number, int quantity, String price) {
        InventoryCard card = StockOperationsServiceTest.card(id, row, name, quantity, price);
        card.setSetCode(set);
        card.setSetName(set);
        card.setCollectorNumber(number);
        card.setCkPriceUsd("1");
        return card;
    }
}
