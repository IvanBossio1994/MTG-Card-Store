package com.tcg.bot.service;

import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import com.tcg.bot.util.Amounts;
import org.springframework.stereotype.Service;

/**
 * Precio puesto a mano para una carta que Card Kingdom (o el catálogo del juego) no cotiza.
 * Se guarda en la columna de precio local del Sheet, así vale para todas las unidades de esa fila.
 * No hace falta otra columna: una carta con precio local y sin precio de referencia es "precio manual".
 */
@Service
public class ManualPriceService {

    private final InventoryStore store;

    public ManualPriceService(InventoryStore store) {
        this.store = store;
    }

    /** Guarda el precio y devuelve la carta actualizada. Busca por ID; si no hay, por fila de Magic. */
    public InventoryCard setPrice(String cardId, int rowIndex, String rawPrice) throws Exception {
        long price = Math.round(Amounts.parse(rawPrice == null ? "" : rawPrice.replace("$", "").trim()));
        if (price <= 0) {
            throw new StockOperationException("Escribí un precio mayor a cero.");
        }

        synchronized (StockOperationsService.writeLock()) {
            InventoryCard card = store.getAllStockCards().stream()
                    .filter(candidate -> cardId != null && !cardId.isBlank()
                            ? cardId.equals(candidate.getId())
                            : candidate.getGame() == null && rowIndex > 0 && candidate.getRowIndex() == rowIndex)
                    .findFirst()
                    .orElseThrow(() -> new StockOperationException(
                            "No se encontró la carta en el Sheet. Sincronizá y volvé a buscarla."));
            store.updateAnyLocalPrice(card, price);
            card.setLocalPrice(String.valueOf(price));
            return card;
        }
    }
}
