package com.tcg.bot.service;

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

class SalesServiceTest {

    private StockOperationsServiceTest.FakeStore store;
    private SalesService service;

    @BeforeEach
    void setUp() {
        store = new StockOperationsServiceTest.FakeStore();
        store.cards.add(card("sol", 2, "Sol Ring", 3, "1500"));
        store.cards.add(card("cs", 3, "Counterspell", 1, "800"));
        var stock = new StockOperationsService(store, Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"), ZoneId.of("America/Buenos_Aires")));
        service = new SalesService(store, stock);
    }

    @Test
    void sellsTheWholeCartAtOnce() throws Exception {
        var ticket = service.sell(List.of(new Item("sol", 2), new Item("cs", 1)));

        assertThat(ticket.units()).isEqualTo(3);
        assertThat(ticket.formattedTotal()).isEqualTo("3.800");
        assertThat(store.card("sol").getQuantity()).isEqualTo("1");
        assertThat(store.card("cs").getQuantity()).isEqualTo("0");
        assertThat(store.cash).hasSize(2).allSatisfy(entry -> assertThat(entry.isSale()).isTrue());
        assertThat(store.cash).extracting(e -> e.getSaleId()).doesNotHaveDuplicates();
    }

    @Test
    void nothingIsChargedIfOneCardIsShort() {
        assertThatThrownBy(() -> service.sell(List.of(new Item("sol", 1), new Item("cs", 2))))
                .isInstanceOf(StockOperationException.class)
                .hasMessageContaining("Counterspell");

        assertThat(store.cash).isEmpty();
        assertThat(store.card("sol").getQuantity()).isEqualTo("3");
    }
}
