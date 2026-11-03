package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/** Acceso a la tienda en memoria de los tests desde otros paquetes. */
public final class TestStores {

    public static class Fake extends StockOperationsServiceTest.FakeStore {
    }

    private TestStores() {
    }

    public static Fake fake() {
        return new Fake();
    }

    public static StockOperationsService stock(InventoryStore store) {
        return new StockOperationsService(store, Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"), ZoneId.of("America/Buenos_Aires")));
    }

    public static List<CashRegisterEntry> cash(Fake store) {
        return store.cash;
    }
}
