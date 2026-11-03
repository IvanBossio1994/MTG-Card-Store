package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.InventoryMovement;

import java.util.List;

/**
 * Lo minimo que necesitan las operaciones de stock y caja del almacenamiento (hoy, el Google Sheet).
 * Existe para poder probar la logica sin pegarle a Google.
 */
public interface InventoryStore {

    List<InventoryCard> getInventoryCards() throws Exception;

    void updateStockState(int rowIndex, InventoryCard card) throws Exception;

    void appendMovement(InventoryMovement movement) throws Exception;

    List<CashRegisterEntry> getCashRegisterEntries() throws Exception;

    void appendCashEntry(CashRegisterEntry entry) throws Exception;
}
