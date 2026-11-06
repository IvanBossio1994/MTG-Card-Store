package com.tcg.bot.service;

import com.tcg.bot.i18n.I18n;
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
    private final CreditService creditService;

    public SalesService(InventoryStore store, StockOperationsService stockOperations, CreditService creditService) {
        this.store = store;
        this.stockOperations = stockOperations;
        this.creditService = creditService;
    }

    public record Ticket(int units, double total, int lines, double creditUsed) {
        public String formattedTotal() {
            return format(total);
        }

        public String formattedCreditUsed() {
            return format(creditUsed);
        }

        public String formattedToPay() {
            return format(total - creditUsed);
        }

        private static String format(double value) {
            return com.tcg.bot.i18n.Money.number(value);
        }
    }

    public Ticket sell(List<ReservationService.Item> items) throws Exception {
        return sell(items, null);
    }

    /** Con {@code creditCustomer}, usa el crédito de tienda de ese cliente (hasta lo que alcance) para pagar. */
    public Ticket sell(List<ReservationService.Item> items, String creditCustomer) throws Exception {
        Map<String, Integer> requested = new LinkedHashMap<>();
        if (items != null) {
            for (ReservationService.Item item : items) {
                if (item.cardId() != null && !item.cardId().isBlank() && item.quantity() > 0) {
                    requested.merge(item.cardId(), item.quantity(), Integer::sum);
                }
            }
        }
        if (requested.isEmpty()) {
            throw new StockOperationException(I18n.t("err.order.empty"));
        }

        synchronized (StockOperationsService.writeLock()) {
            List<InventoryCard> cards = store.getAllStockCards();

            // Primero se valida todo: si falta stock o precio de alguna carta, no se cobra nada.
            List<InventoryCard> targets = new ArrayList<>();
            for (var entry : requested.entrySet()) {
                InventoryCard card = cards.stream()
                        .filter(candidate -> entry.getKey().equals(candidate.getId()))
                        .findFirst()
                        .orElseThrow(() -> new StockOperationException(
                                I18n.t("err.orderCardGone")));
                if (Amounts.parse(card.getLocalPrice()) <= 0) {
                    throw new StockOperationException(card.getName()
                            + " no tiene precio local. Sincronizá precios antes de cobrarla.");
                }
                if (StockOperationsService.quantity(card) < entry.getValue()) {
                    throw new StockOperationException(I18n.t("err.notEnoughStock", card.getName(), StockOperationsService.quantity(card), entry.getValue()));
                }
                targets.add(card);
            }
            stockOperations.requireFreeOperation();

            LocalDateTime now = LocalDateTime.now(stockOperations.clock);
            int units = 0;
            double total = 0;
            for (InventoryCard card : targets) {
                int quantity = requested.get(card.getId());
                double unitPrice = Amounts.parse(card.getLocalPrice());
                int previous = StockOperationsService.quantity(card);
                int next = previous - quantity;

                StockOperationsService.setQuantity(card, next);
                store.updateAnyStock(card);

                CashRegisterEntry sale = stockOperations.cashEntry(now, card, CashRegisterEntry.TYPE_SALE, quantity, unitPrice);
                sale.setSaleId(StockOperationsService.newSaleId());
                try {
                    store.appendCashEntry(sale);
                } catch (Exception e) {
                    StockOperationsService.setQuantity(card, previous);
                    store.updateAnyStock(card);
                    throw new StockOperationException(I18n.t(units > 0 ? "sale.err.cutPartial" : "sale.err.cutNone", card.getName()));
                }

                stockOperations.appendMovementQuietly(stockOperations.movement(
                        now, -quantity, card, previous, next, StockOperationsService.SOURCE_SOLD));
                units += quantity;
                total += unitPrice * quantity;
            }

            double creditUsed = 0;
            if (creditCustomer != null && !creditCustomer.isBlank()) {
                creditUsed = Math.min(Math.max(creditService.balance(creditCustomer), 0), total);
                if (!com.tcg.bot.i18n.Money.isZero(creditUsed)) {
                    creditUsed = com.tcg.bot.i18n.Money.round(creditUsed);
                    String reference = "V" + StockOperationsService.newSaleId();
                    creditService.add(creditCustomer, "", -creditUsed, I18n.t("sale.credit.reason"), reference);
                    CashRegisterEntry credit = new CashRegisterEntry();
                    credit.setDate(now.toLocalDate().toString());
                    credit.setTime(now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
                    credit.setType(CashRegisterEntry.TYPE_CREDIT);
                    credit.setName(I18n.t("sale.credit.entry", creditCustomer.trim()));
                    credit.setQuantity("0");
                    credit.setTotal(com.tcg.bot.i18n.Money.plain(creditUsed));
                    credit.setSaleId(reference);
                    store.appendCashEntry(credit);
                } else {
                    creditUsed = 0;
                }
            }

            return new Ticket(units, total, targets.size(), creditUsed);
        }
    }
}
