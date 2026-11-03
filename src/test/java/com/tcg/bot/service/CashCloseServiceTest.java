package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class CashCloseServiceTest {

    static CashRegisterEntry entry(String date, String type, String quantity, String total) {
        CashRegisterEntry entry = new CashRegisterEntry();
        entry.setDate(date);
        entry.setTime("12:00:00");
        entry.setType(type);
        entry.setName("Sol Ring");
        entry.setQuantity(quantity);
        entry.setTotal(total);
        return entry;
    }

    @Test
    void comparesCountedMoneyWithTheDaysSalesAndLeavesTotalsUntouched() throws Exception {
        var store = new StockOperationsServiceTest.FakeStore();
        store.cash.add(entry("2026-11-03", CashRegisterEntry.TYPE_SALE, "2", "3000"));
        store.cash.add(entry("2026-11-03", CashRegisterEntry.TYPE_SALE, "1", "1000"));
        store.cash.add(entry("2026-11-03", CashRegisterEntry.TYPE_VOID, "1", "1000"));
        store.cash.add(entry("2026-11-02", CashRegisterEntry.TYPE_SALE, "1", "9000"));
        var clock = Clock.fixed(Instant.parse("2026-11-03T23:00:00Z"), ZoneId.of("America/Buenos_Aires"));
        var service = new CashCloseService(store, new StockOperationsService(store, clock));

        var day = service.day(null);
        assertThat(day.date()).isEqualTo("2026-11-03");
        assertThat(day.grossSales()).isEqualTo("4.000");
        assertThat(day.voids()).isEqualTo("1.000");
        assertThat(day.expected()).isEqualTo("3.000");

        double difference = service.close("2026-11-03", 2000, 500, 0, "faltó cambio");

        assertThat(difference).isEqualTo(-500);
        CashRegisterEntry close = store.cash.get(store.cash.size() - 1);
        assertThat(close.isClose()).isTrue();
        assertThat(close.affectsCash()).isFalse();
        assertThat(close.getName()).contains("contado $ 2.500").contains("diferencia -$ 500").contains("faltó cambio");
        assertThat(service.day("2026-11-03").expected()).isEqualTo("3.000");
        assertThat(service.day("2026-11-03").closes()).hasSize(1);
    }
}
