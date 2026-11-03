package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.PricePoint;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static com.tcg.bot.service.StockOperationsServiceTest.card;
import static org.assertj.core.api.Assertions.assertThat;

class ReviewServiceTest {

    static class Store extends StockOperationsServiceTest.FakeStore {
        final List<PricePoint> points = new ArrayList<>();

        @Override
        public List<PricePoint> getPriceHistory() {
            return points;
        }
    }

    static CashRegisterEntry sale(String date, String cardId, int units) {
        CashRegisterEntry entry = new CashRegisterEntry();
        entry.setDate(date);
        entry.setType(CashRegisterEntry.TYPE_SALE);
        entry.setCardId(cardId);
        entry.setQuantity(String.valueOf(units));
        entry.setTotal("1000");
        return entry;
    }

    @Test
    void flagsLowStockBigPriceMovesMissingPriceAndEdition() throws Exception {
        Store store = new Store();
        store.cards.add(card("sol", 2, "Sol Ring", 1, "5000"));
        store.cards.add(card("cs", 3, "Counterspell", 4, "800"));
        store.cards.add(card("np", 4, "Sin Precio", 2, ""));
        InventoryCard bare = card("bare", 5, "Elli's Rage", 1, "1000");
        bare.setSetCode("");
        bare.setSetName("");
        bare.setCollectorNumber("");
        store.cards.add(bare);
        store.cash.add(sale("2026-10-20", "sol", 2));
        store.cash.add(sale("2026-08-01", "cs", 3));
        store.points.add(new PricePoint("2026-09-01", "sol", "Sol Ring", "", "CMM", "410", "", 2.0, null, null, 4000.0));
        store.points.add(new PricePoint("2026-11-01", "sol", "Sol Ring", "", "CMM", "410", "", 2.5, null, null, 5000.0));

        Clock clock = Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"), ZoneId.of("America/Buenos_Aires"));
        var review = new ReviewService(store, new PriceHistoryService(store, clock),
                new StockOperationsService(store, clock)).review();

        assertThat(review.lowStock()).extracting(item -> item.card().getId()).containsExactly("sol");
        assertThat(review.priceMoves()).extracting(PriceHistoryService.Trend::cardId).containsExactly("sol");
        assertThat(review.withoutPrice()).extracting(InventoryCard::getId).containsExactly("np");
        assertThat(review.withoutEdition()).extracting(InventoryCard::getId).containsExactly("bare");
        assertThat(review.total()).isEqualTo(4);
    }
}
