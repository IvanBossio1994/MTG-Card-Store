package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryMovement;
import com.tcg.bot.model.Reservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.tcg.bot.service.StockOperationsServiceTest.card;
import static org.assertj.core.api.Assertions.assertThat;

/** El reloj de los tests marca 2026-11-03. */
class ReportServiceTest {

    private StockOperationsServiceTest.FakeStore store;
    private ReportService service;

    @BeforeEach
    void setUp() {
        store = new StockOperationsServiceTest.FakeStore();
        var sol = card("sol", 2, "Sol Ring", 2, "1500");
        sol.setCost("1000");
        store.cards.add(sol);
        store.cards.add(card("bolt", 3, "Lightning Bolt", 4, "2000"));
        var old = card("old", 4, "Counterspell", 1, "5000");
        old.setSetName("Mercadian Masques");
        old.setCollectorNumber("69");
        store.cards.add(old);

        store.cash.add(sale("2026-11-01", "sol", "Sol Ring", 3, 1500, "1000"));
        store.cash.add(sale("2026-10-20", "bolt", "Lightning Bolt", 1, 2000, ""));
        store.cash.add(sale("2025-01-10", "old", "Counterspell", 1, 5000, ""));
        // Lightning Bolt entró hace poco: no está quieta aunque se haya vendido poco.
        store.movements.add(new InventoryMovement("2026-10-30 10:00:00", "2026-10-30", "10:00:00", "ENTRADA", "4",
                "Lightning Bolt", "Commander Masters", "CMM", "410", "nonfoil", "0", "4", "Agregado al stock"));

        service = new ReportService(store, TestStores.stock(store));
    }

    @Test
    void salesProfitAndBestSellersOfThePeriod() throws Exception {
        var report = service.report(30);

        assertThat(report.sales()).isEqualTo(3 * 1500 + 2000);
        assertThat(report.units()).isEqualTo(4);
        assertThat(report.profit().value()).isEqualTo(3 * 500);
        assertThat(report.profit().withoutCost()).isEqualTo(1);
        assertThat(report.topCards()).extracting(ReportService.TopCard::name).containsExactly("Sol Ring", "Lightning Bolt");
        assertThat(report.topCards().get(0).profit()).isEqualTo(1500);
        assertThat(report.topCards().get(1).profit()).isNull();
    }

    @Test
    void stockValueAtPriceAndAtCost() throws Exception {
        var stock = service.report(30).stock();

        assertThat(stock.atPrice()).isEqualTo(2 * 1500 + 4 * 2000 + 5000);
        assertThat(stock.atCost()).isEqualTo(2 * 1000);
        assertThat(stock.potentialProfit()).isEqualTo(2 * 500);
        assertThat(stock.unitsWithoutCost()).isEqualTo(5);
    }

    @Test
    void stillCardsAreTheOnesWithoutRecentSalesOrEntries() throws Exception {
        var report = service.report(30);

        assertThat(report.still()).extracting(still -> still.card().getId()).containsExactly("old");
        assertThat(report.still().get(0).lastActivity()).isEqualTo("2025-01-10");
    }

    @Test
    void customersComeFromPickedUpReservations() throws Exception {
        store.reservations.add(reservation("R1", "Juan", "RETIRADA", 2, "1500"));
        store.reservations.add(reservation("R2", "juan ", "RETIRADA", 1, "2000"));
        store.reservations.add(reservation("R3", "Ana", "CANCELADA", 5, "2000"));

        var customers = service.report(30).customers();

        assertThat(customers).hasSize(1);
        assertThat(customers.get(0).name()).isEqualTo("Juan");
        assertThat(customers.get(0).orders()).isEqualTo(2);
        assertThat(customers.get(0).total()).isEqualTo(5000);
    }

    private static CashRegisterEntry sale(String date, String cardId, String name, int units, double price, String cost) {
        CashRegisterEntry entry = new CashRegisterEntry();
        entry.setDate(date);
        entry.setType(CashRegisterEntry.TYPE_SALE);
        entry.setCardId(cardId);
        entry.setName(name);
        entry.setQuantity(String.valueOf(units));
        entry.setLocalPrice(String.valueOf(price));
        entry.setTotal(String.valueOf(price * units));
        entry.setUnitCost(cost);
        entry.setSaleId(date + cardId);
        return entry;
    }

    private static Reservation reservation(String id, String customer, String status, int units, String price) {
        Reservation line = new Reservation();
        line.setReservationId(id);
        line.setDate("2026-11-01");
        line.setCustomer(customer);
        line.setStatus(status);
        line.setQuantity(String.valueOf(units));
        line.setUnitPrice(price);
        return line;
    }
}
