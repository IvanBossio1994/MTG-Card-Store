package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Deja que otros paquetes (por ejemplo, los otros juegos) anoten ventas y movimientos como lo hace Magic. */
@Component
public class StockOperationsBridge {

    private final InventoryStore store;
    private final StockOperationsService stockOperations;

    public StockOperationsBridge(InventoryStore store, StockOperationsService stockOperations) {
        this.store = store;
        this.stockOperations = stockOperations;
    }

    public void movement(InventoryCard card, int change, int previous, int next, String source) {
        stockOperations.appendMovementQuietly(stockOperations.movement(
                LocalDateTime.now(stockOperations.clock), change, card, previous, next, source));
    }

    public String sale(InventoryCard card, int units, double unitPrice) throws Exception {
        CashRegisterEntry sale = stockOperations.cashEntry(
                LocalDateTime.now(stockOperations.clock), card, CashRegisterEntry.TYPE_SALE, units, unitPrice);
        String saleId = StockOperationsService.newSaleId();
        sale.setSaleId(saleId);
        store.appendCashEntry(sale);
        return saleId;
    }
}
