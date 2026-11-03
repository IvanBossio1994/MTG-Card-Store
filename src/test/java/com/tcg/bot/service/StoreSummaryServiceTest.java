package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.InventoryMovement;
import com.tcg.bot.model.Reservation;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoreSummaryServiceTest {

    static InventoryCard card(String name, String quantity, String price) {
        InventoryCard card = new InventoryCard();
        card.setName(name);
        card.setQuantity(quantity);
        card.setLocalPrice(price);
        return card;
    }

    static CashRegisterEntry cash(String date, String type, String total, boolean voided) {
        CashRegisterEntry entry = new CashRegisterEntry();
        entry.setDate(date);
        entry.setType(type);
        entry.setTotal(total);
        entry.setVoided(voided);
        return entry;
    }

    static Reservation reservation(String id, String status, String expiresOn) {
        Reservation line = new Reservation();
        line.setReservationId(id);
        line.setStatus(status);
        line.setExpiresOn(expiresOn);
        return line;
    }

    @Test
    void summarizesTodayFromTheSheet() {
        InventoryStore store = new InventoryStore() {
            public List<InventoryCard> getInventoryCards() {
                return List.of(card("Sol Ring", "3", "4.500"), card("Varina", "1", ""), card("Agotada", "0", "9000"));
            }
            public void updateStockState(int rowIndex, InventoryCard card) { }
            public void appendMovement(InventoryMovement movement) { }
            public List<CashRegisterEntry> getCashRegisterEntries() {
                return List.of(
                        cash("2026-11-03", CashRegisterEntry.TYPE_SALE, "4.500", false),
                        cash("2026-11-03", CashRegisterEntry.TYPE_SALE, "2.000", true),
                        cash("2026-11-03", CashRegisterEntry.TYPE_VOID, "2.000", false),
                        cash("2026-11-02", CashRegisterEntry.TYPE_SALE, "9.999", false));
            }
            public void appendCashEntry(CashRegisterEntry entry) { }
            public List<Reservation> getReservations() {
                return List.of(
                        reservation("R1", Reservation.STATUS_ACTIVE, "2026-11-03"),
                        reservation("R1", Reservation.STATUS_ACTIVE, "2026-11-03"),
                        reservation("R2", Reservation.STATUS_ACTIVE, "2026-11-10"),
                        reservation("R3", Reservation.STATUS_PICKED_UP, "2026-11-03"));
            }
            public void appendReservations(List<Reservation> reservations) { }
            public void updateReservationStatuses(List<Reservation> reservations) { }
        };

        StoreSummaryService.Summary summary = new StoreSummaryService(store,
                Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"), ZoneId.of("America/Buenos_Aires"))).summary();

        assertThat(summary.salesToday()).isEqualTo("4.500");
        assertThat(summary.salesCountToday()).isEqualTo(1);
        assertThat(summary.activeReservations()).isEqualTo(2);
        assertThat(summary.reservationsDueToday()).isEqualTo(1);
        assertThat(summary.unitsInStock()).isEqualTo(4);
        assertThat(summary.distinctInStock()).isEqualTo(2);
        assertThat(summary.stockValue()).isEqualTo("13.500");
        assertThat(summary.cardsWithoutPrice()).isEqualTo(1);
    }
}
