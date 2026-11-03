package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.InventoryMovement;
import com.tcg.bot.model.PricePoint;
import com.tcg.bot.model.Reservation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PriceHistoryServiceTest {

    static class Store implements InventoryStore {
        final List<PricePoint> points = new ArrayList<>();
        public List<InventoryCard> getInventoryCards() { return List.of(); }
        public void updateStockState(int rowIndex, InventoryCard card) { }
        public void appendMovement(InventoryMovement movement) { }
        public List<CashRegisterEntry> getCashRegisterEntries() { return List.of(); }
        public void appendCashEntry(CashRegisterEntry entry) { }
        public List<Reservation> getReservations() { return List.of(); }
        public void appendReservations(List<Reservation> reservations) { }
        public void updateReservationStatuses(List<Reservation> reservations) { }
        @Override public List<PricePoint> getPriceHistory() { return List.copyOf(points); }
        @Override public void appendPriceHistory(List<PricePoint> added) { points.addAll(added); }
    }

    static InventoryCard card(String id, String ck, String local) {
        InventoryCard card = new InventoryCard();
        card.setId(id);
        card.setName("Sol Ring");
        card.setSetName("Commander Masters");
        card.setSetCode("CMM");
        card.setCollectorNumber("410");
        card.setPrinting("nonfoil");
        card.setCkPriceUsd(ck);
        card.setLocalPrice(local);
        return card;
    }

    static PriceHistoryService at(Store store, String instant) {
        return new PriceHistoryService(store, Clock.fixed(Instant.parse(instant), ZoneId.of("America/Buenos_Aires")));
    }

    @Test
    void recordsOnlyWhenPriceChanges() throws Exception {
        Store store = new Store();
        Map<String, ScryfallPriceService.Prices> extra = Map.of(
                ScryfallPriceService.key("CMM", "410"), new ScryfallPriceService.Prices(1.9, null, 1.5, null));

        assertThat(at(store, "2026-10-01T15:00:00Z").record(List.of(card("a", "2.49", "4500")), extra)).isEqualTo(1);
        assertThat(at(store, "2026-10-02T15:00:00Z").record(List.of(card("a", "2.49", "4500")), extra)).isZero();
        assertThat(at(store, "2026-11-03T15:00:00Z").record(List.of(card("a", "2.99", "5000")), extra)).isEqualTo(1);

        assertThat(store.points).hasSize(2);
        assertThat(store.points.get(0).tcgplayerUsd()).isEqualTo(1.9);
        assertThat(store.points.get(1).date()).isEqualTo("2026-11-03");
    }

    @Test
    void trendComparesWithAMonthAgo() throws Exception {
        Store store = new Store();
        at(store, "2026-09-20T15:00:00Z").record(List.of(card("a", "2.00", "4000")), Map.of());
        at(store, "2026-10-20T15:00:00Z").record(List.of(card("a", "2.40", "4500")), Map.of());

        var service = at(store, "2026-11-03T15:00:00Z");
        var trends = service.trends(List.of(card("a", "2.40", "5000")), store.getPriceHistory());

        // Referencia: el ultimo precio de hace 30 dias o mas (4000), no el de hace dos semanas.
        assertThat(trends.get(0).changePercent()).isEqualTo(25.0);
        assertThat(PriceHistoryService.formatPercent(25.0)).isEqualTo("+25%");
    }
}
