package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.service.BuyService.Line;
import com.tcg.bot.service.BuyService.Payment;
import com.tcg.bot.service.ReservationService.Item;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static com.tcg.bot.service.StockOperationsServiceTest.card;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plan gratis: 300 ventas o compras por mes calendario, inventario sin límite. */
class FreePlanOperationsTest {

    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    private StockOperationsServiceTest.FakeStore store;
    private StockOperationsService stock;
    private boolean pro;

    @BeforeEach
    void setUp() {
        store = new StockOperationsServiceTest.FakeStore();
        store.cards.add(card("sol", 2, "Sol Ring", 5, "1500"));
        store.cards.add(card("cs", 3, "Counterspell", 5, "800"));
        stock = new StockOperationsService(store, Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZoneId.of("America/Buenos_Aires")));
        stock.setUnlimitedOperations(() -> pro);
    }

    private static CashRegisterEntry entry(String type, String date, String time, String reference) {
        CashRegisterEntry entry = new CashRegisterEntry();
        entry.setType(type);
        entry.setDate(date);
        entry.setTime(time);
        entry.setSaleId(reference);
        entry.setQuantity("1");
        entry.setTotal("100");
        return entry;
    }

    /** Llena la caja con {@code count} ventas sueltas de noviembre. */
    private void usedThisMonth(int count) {
        for (int i = 0; i < count; i++) {
            store.cash.add(entry(CashRegisterEntry.TYPE_SALE, "2026-11-05",
                    String.format("%02d:%02d:%02d", i / 3600, i / 60 % 60, i % 60), "S" + i));
        }
    }

    @Test
    void aCartOrAPurchaseCountsOnceAndOnlyThisMonth() {
        List<CashRegisterEntry> entries = List.of(
                // Carrito de dos cartas: misma fecha y hora, una operación.
                entry(CashRegisterEntry.TYPE_SALE, "2026-11-02", "10:00:00", "a1"),
                entry(CashRegisterEntry.TYPE_SALE, "2026-11-02", "10:00:00", "a2"),
                entry(CashRegisterEntry.TYPE_CREDIT, "2026-11-02", "10:00:00", "V1"),
                // Compra: una fila con su referencia.
                entry(CashRegisterEntry.TYPE_BUY, "2026-11-03", "11:00:00", "C1"),
                // Anular no devuelve la operación ni suma otra.
                entry(CashRegisterEntry.TYPE_VOID, "2026-11-04", "12:00:00", "x"),
                // Ni el cierre de caja ni el pago a un consignador.
                entry(CashRegisterEntry.TYPE_CLOSE, "2026-11-04", "20:00:00", ""),
                entry(CashRegisterEntry.TYPE_BUY, "2026-11-04", "21:00:00", "K1"),
                // Octubre no cuenta.
                entry(CashRegisterEntry.TYPE_SALE, "2026-10-31", "23:59:59", "old"));

        assertThat(LicenseService.countOperations(entries, NOVEMBER)).isEqualTo(2);
    }

    @Test
    void the300thOperationGoesThroughAndThe301stIsBlocked() throws Exception {
        usedThisMonth(299);
        var sales = new SalesService(store, stock, new CreditService(store, stock));

        sales.sell(List.of(new Item("sol", 1), new Item("cs", 1)));
        assertThat(stock.operationsThisMonth()).isEqualTo(300);

        assertThatThrownBy(() -> sales.sell(List.of(new Item("sol", 1))))
                .isInstanceOf(StockOperationException.class)
                .hasMessageContaining("300");
        assertThat(store.card("sol").getQuantity()).isEqualTo("4");
    }

    @Test
    void everyWayOfChargingOrBuyingIsBlockedAtTheLimit() throws Exception {
        usedThisMonth(300);
        int cashRows = store.cash.size();

        assertThatThrownBy(() -> stock.changeStock("sol", 2, -1, StockOperationsService.Reason.SALE))
                .isInstanceOf(StockOperationException.class);
        assertThatThrownBy(() -> new StockOperationsBridge(store, stock).requireFreeOperation())
                .isInstanceOf(StockOperationException.class);
        var buy = new BuyService(store, stock, new CreditService(store, stock), new WantedService(store, stock));
        assertThatThrownBy(() -> buy.buy("Ana", "", Payment.CASH, List.of(new Line(store.card("sol"), 1, 500))))
                .isInstanceOf(StockOperationException.class);

        // Reservar sigue andando, pero retirar (cobrar) no.
        var reservations = new ReservationService(store, stock);
        String id = reservations.create("Juan", "", 7, List.of(new Item("cs", 1)));
        assertThatThrownBy(() -> reservations.pickUp(id)).isInstanceOf(StockOperationException.class);

        assertThat(store.cash).hasSize(cashRows);
        assertThat(store.card("sol").getQuantity()).isEqualTo("5");
    }

    @Test
    void inventoryAdjustmentsAreNeverLimited() throws Exception {
        usedThisMonth(300);

        stock.changeStock("sol", 2, 10, StockOperationsService.Reason.ADJUST);
        stock.changeStock("sol", 2, -3, StockOperationsService.Reason.ADJUST);

        assertThat(store.card("sol").getQuantity()).isEqualTo("12");
    }

    @Test
    void lastMonthsOperationsDoNotCount() throws Exception {
        for (int i = 0; i < 400; i++) {
            store.cash.add(entry(CashRegisterEntry.TYPE_SALE, "2026-10-15", "10:00:" + i, "S" + i));
        }

        stock.changeStock("sol", 2, -1, StockOperationsService.Reason.SALE);

        assertThat(stock.operationsThisMonth()).isEqualTo(1);
    }

    @Test
    void storePlanHasNoLimit() throws Exception {
        usedThisMonth(500);
        pro = true;

        stock.changeStock("sol", 2, -1, StockOperationsService.Reason.SALE);

        assertThat(store.card("sol").getQuantity()).isEqualTo("4");
    }
}
