package com.tcg.bot.service;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import com.tcg.bot.util.Amounts;
import org.springframework.stereotype.Service;

/**
 * Costo y ubicación de una carta. El costo sale solo de las compras a clientes (promedio ponderado)
 * y también se puede cargar a mano; con él la caja calcula la ganancia de cada venta.
 */
@Service
public class CardDetailsService {

    private final InventoryStore store;

    public CardDetailsService(InventoryStore store) {
        this.store = store;
    }

    /**
     * Guarda costo y ubicación. Un costo vacío lo borra (no se sabe); la ubicación vacía también.
     * Busca por ID; si no hay, por fila de Magic.
     */
    public InventoryCard save(String cardId, int rowIndex, String rawCost, String location) throws Exception {
        String costText = rawCost == null ? "" : rawCost.replace(com.tcg.bot.i18n.Money.symbol(), "").trim();
        Double cost = costText.isEmpty() ? null : Amounts.parseOrNull(costText);
        if (!costText.isEmpty() && (cost == null || cost < 0)) {
            throw new StockOperationException(I18n.t("details.err.cost"));
        }
        String place = location == null ? "" : location.trim();
        if (place.length() > 60) {
            throw new StockOperationException(I18n.t("details.err.location"));
        }

        synchronized (StockOperationsService.writeLock()) {
            InventoryCard card = store.getAllStockCards().stream()
                    .filter(candidate -> cardId != null && !cardId.isBlank()
                            ? cardId.equals(candidate.getId())
                            : candidate.getGame() == null && rowIndex > 0 && candidate.getRowIndex() == rowIndex)
                    .findFirst()
                    .orElseThrow(() -> new StockOperationException(I18n.t("err.cardNotInInventory")));
            card.setCost(cost == null ? "" : com.tcg.bot.i18n.Money.plain(cost));
            card.setLocation(place);
            store.updateAnyDetails(card);
            return card;
        }
    }

    /**
     * Costo promedio después de comprar {@code bought} unidades a {@code unitPaid} cada una.
     * Si las unidades que ya había no tenían costo, se toma lo pagado ahora como costo de todas:
     * es la mejor estimación disponible y evita que la carta quede sin costo para siempre.
     */
    public static double averageCost(int previousUnits, Double previousCost, int bought, double unitPaid) {
        int previous = Math.max(previousUnits, 0);
        if (previousCost == null || previous == 0) {
            return com.tcg.bot.i18n.Money.round(unitPaid);
        }
        return com.tcg.bot.i18n.Money.round((previous * previousCost + bought * unitPaid) / (previous + bought));
    }
}
