package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import com.tcg.bot.util.Amounts;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Venta de varias cartas juntas (el pedido armado desde la búsqueda), cobrada en el momento.
 * Cada carta queda como una línea de VENTA en la caja, así se puede anular por separado.
 */
@Service
public class SalesService {

    private final InventoryStore store;
    private final StockOperationsService stockOperations;

    public SalesService(InventoryStore store, StockOperationsService stockOperations) {
        this.store = store;
        this.stockOperations = stockOperations;
    }

    public record Ticket(int units, double total, int lines) {
        public String formattedTotal() {
            return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(new Locale("es", "AR"))).format(total);
        }
    }

    public Ticket sell(List<ReservationService.Item> items) throws Exception {
        Map<String, Integer> requested = new LinkedHashMap<>();
        if (items != null) {
            for (ReservationService.Item item : items) {
                if (item.cardId() != null && !item.cardId().isBlank() && item.quantity() > 0) {
                    requested.merge(item.cardId(), item.quantity(), Integer::sum);
                }
            }
        }
        if (requested.isEmpty()) {
            throw new StockOperationException("El pedido no tiene cartas.");
        }

        synchronized (StockOperationsService.writeLock()) {
            List<InventoryCard> cards = store.getInventoryCards();

            // Primero se valida todo: si falta stock o precio de alguna carta, no se cobra nada.
            List<InventoryCard> targets = new ArrayList<>();
            for (var entry : requested.entrySet()) {
                InventoryCard card = cards.stream()
                        .filter(candidate -> entry.getKey().equals(candidate.getId()))
                        .findFirst()
                        .orElseThrow(() -> new StockOperationException(
                                "Una de las cartas ya no está en el inventario. Volvé a armar el pedido."));
                if (Amounts.parse(card.getLocalPrice()) <= 0) {
                    throw new StockOperationException(card.getName()
                            + " no tiene precio local. Sincronizá precios antes de cobrarla.");
                }
                if (StockOperationsService.quantity(card) < entry.getValue()) {
                    throw new StockOperationException("No alcanza el stock de " + card.getName()
                            + ": hay " + StockOperationsService.quantity(card) + " y pediste " + entry.getValue() + ".");
                }
                targets.add(card);
            }

            LocalDateTime now = LocalDateTime.now(stockOperations.clock);
            int units = 0;
            double total = 0;
            for (InventoryCard card : targets) {
                int quantity = requested.get(card.getId());
                double unitPrice = Amounts.parse(card.getLocalPrice());
                int previous = StockOperationsService.quantity(card);
                int next = previous - quantity;

                StockOperationsService.setQuantity(card, next);
                store.updateStockState(card.getRowIndex(), card);

                CashRegisterEntry sale = stockOperations.cashEntry(now, card, CashRegisterEntry.TYPE_SALE, quantity, unitPrice);
                sale.setSaleId(StockOperationsService.newSaleId());
                try {
                    store.appendCashEntry(sale);
                } catch (Exception e) {
                    StockOperationsService.setQuantity(card, previous);
                    store.updateStockState(card.getRowIndex(), card);
                    throw new StockOperationException("Se cortó la conexión con el Sheet al cobrar " + card.getName()
                            + (units > 0 ? ". Las cartas anteriores sí quedaron cobradas; revisá la caja." : ". No se cobró nada."));
                }

                stockOperations.appendMovementQuietly(stockOperations.movement(
                        now, -quantity, card, previous, next, StockOperationsService.SOURCE_SOLD));
                units += quantity;
                total += unitPrice * quantity;
            }

            return new Ticket(units, total, targets.size());
        }
    }
}
