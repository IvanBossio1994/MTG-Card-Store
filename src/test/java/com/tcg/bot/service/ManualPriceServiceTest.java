package com.tcg.bot.service;

import com.tcg.bot.model.Game;
import com.tcg.bot.model.GameCard;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.service.ReservationService.Item;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static com.tcg.bot.service.StockOperationsServiceTest.card;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualPriceServiceTest {

    private StockOperationsServiceTest.FakeStore store;
    private ManualPriceService service;
    private SalesService sales;

    @BeforeEach
    void setUp() {
        store = new StockOperationsServiceTest.FakeStore();
        store.cards.add(card("edgar", 2, "Edgar Markov", 2, ""));
        service = new ManualPriceService(store);
        var stock = new StockOperationsService(store, Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneId.of("America/Buenos_Aires")));
        sales = new SalesService(store, stock, new CreditService(store, stock));
    }

    @Test
    void aCardWithoutPriceCanBeSoldOnceThePriceIsSetByHand() throws Exception {
        assertThatThrownBy(() -> sales.sell(List.of(new Item("edgar", 1))))
                .isInstanceOf(StockOperationException.class);

        service.setPrice("edgar", 0, "50.000");

        assertThat(store.card("edgar").getLocalPrice()).isEqualTo("50000");
        var ticket = sales.sell(List.of(new Item("edgar", 2)));
        assertThat(ticket.formattedTotal()).isEqualTo("100.000");
    }

    @Test
    void findsAMagicRowByNumberWhenThereIsNoId() throws Exception {
        store.cards.add(card(null, 5, "Sin ID", 1, ""));

        service.setPrice("", 5, "$ 1.200");

        assertThat(store.cards.get(1).getLocalPrice()).isEqualTo("1200");
    }

    @Test
    void rejectsZeroOrText() {
        assertThatThrownBy(() -> service.setPrice("edgar", 0, "abc"))
                .isInstanceOf(StockOperationException.class);
        assertThatThrownBy(() -> service.setPrice("edgar", 0, "0"))
                .isInstanceOf(StockOperationException.class);
    }

    @Test
    void savesThePriceInTheOtherGamesTab() throws Exception {
        GameCard exodia = new GameCard();
        exodia.setName("The Legendary Exodia");
        exodia.setQuantity(1);
        exodia.setId("Gexodia");
        store.appendGameCard(Game.YUGIOH, exodia);

        service.setPrice("Gexodia", 0, "30000");

        GameCard saved = store.getGameCards(Game.YUGIOH).get(0);
        assertThat(saved.getLocalPrice()).isEqualTo(30000);
        assertThat(saved.isManualPrice()).isTrue();
    }

    @Test
    void aPriceWithoutCardKingdomPriceIsManual() {
        InventoryCard manual = card("a", 2, "A", 1, "50000");
        manual.setCkPriceUsd("");
        InventoryCard normal = card("b", 3, "B", 1, "7000");
        normal.setCkPriceUsd("3.99");

        assertThat(manual.isManualPrice()).isTrue();
        assertThat(normal.isManualPrice()).isFalse();
        assertThat(card("c", 4, "C", 1, "").isManualPrice()).isFalse();
    }
}
