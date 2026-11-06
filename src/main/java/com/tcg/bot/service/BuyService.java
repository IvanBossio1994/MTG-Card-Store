package com.tcg.bot.service;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Compra de cartas a un cliente: las cartas entran al stock y se paga en efectivo (sale del cajón)
 * o con crédito de tienda (se suma al saldo del cliente).
 */
@Service
public class BuyService {

    public static final String SOURCE_BOUGHT = "Comprada a cliente";

    public enum Payment { CASH, CREDIT }

    /** Una carta comprada: la fila del inventario (ya existente), cuántas y cuánto se paga por cada una. */
    public record Line(InventoryCard card, int quantity, double unitPaid) {
    }

    /** {@code waiting}: clientes que buscaban alguna de las cartas que acaban de entrar. */
    public record Result(int units, double total, Payment payment, double newCreditBalance,
                         List<com.tcg.bot.model.WantedCard> waiting) {
        public String formattedTotal() {
            return CreditService.ars(total);
        }

        public String formattedBalance() {
            return CreditService.ars(newCreditBalance);
        }
    }

    private final InventoryStore store;
    private final StockOperationsService stockOperations;
    private final CreditService creditService;
    private final WantedService wantedService;

    public BuyService(InventoryStore store, StockOperationsService stockOperations, CreditService creditService,
                      WantedService wantedService) {
        this.store = store;
        this.stockOperations = stockOperations;
        this.creditService = creditService;
        this.wantedService = wantedService;
    }

    /** El aviso de cartas buscadas es una ayuda: si falla la lectura, la compra igual queda registrada. */
    private List<com.tcg.bot.model.WantedCard> waitingQuietly(List<Line> lines) {
        try {
            return wantedService.waitingFor(lines.stream().map(Line::card).toList());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BuyService.class).debug("No se pudieron revisar las buscadas: {}", e.getMessage());
            return List.of();
        }
    }

    /** El costo es un dato de ayuda: si no se puede guardar, la compra igual queda registrada. */
    private void updateCostQuietly(InventoryCard card) {
        try {
            store.updateAnyDetails(card);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BuyService.class).warn("No se pudo guardar el costo de {}.", card.getName(), e);
        }
    }

    public Result buy(String customer, String contact, Payment payment, List<Line> lines) throws Exception {
        if (lines == null || lines.isEmpty()) {
            throw new StockOperationException(I18n.t("buy.err.empty"));
        }
        if (payment == Payment.CREDIT && (customer == null || customer.isBlank())) {
            throw new StockOperationException(I18n.t("buy.err.customer"));
        }
        for (Line line : lines) {
            if (line.quantity() <= 0 || line.unitPaid() < 0) {
                throw new StockOperationException(I18n.t("buy.err.line", line.card().getName()));
            }
        }

        String who = customer == null || customer.isBlank() ? I18n.t("buy.defaultCustomer") : customer.trim();
        synchronized (StockOperationsService.writeLock()) {
            LocalDateTime now = LocalDateTime.now(stockOperations.clock);
            String reference = "C" + StockOperationsService.newSaleId();
            int units = 0;
            double total = 0;

            for (Line line : lines) {
                InventoryCard card = line.card();
                int previous = StockOperationsService.quantity(card);
                int next = previous + line.quantity();
                StockOperationsService.setQuantity(card, next);
                store.updateAnyStock(card);
                double cost = CardDetailsService.averageCost(previous, card.costValue(), line.quantity(), line.unitPaid());
                card.setCost(com.tcg.bot.i18n.Money.plain(cost));
                updateCostQuietly(card);
                stockOperations.appendMovementQuietly(stockOperations.movement(
                        now, line.quantity(), card, previous, next, SOURCE_BOUGHT + " (" + who + ")"));
                units += line.quantity();
                total += com.tcg.bot.i18n.Money.round(line.unitPaid()) * line.quantity();
            }

            CashRegisterEntry entry = new CashRegisterEntry();
            entry.setDate(now.toLocalDate().toString());
            entry.setTime(now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            entry.setType(CashRegisterEntry.TYPE_BUY);
            entry.setQuantity(String.valueOf(units));
            entry.setSaleId(reference);
            double balance = 0;
            if (payment == Payment.CASH) {
                entry.setName(I18n.t("buy.entry.cash", who, units));
                entry.setTotal(com.tcg.bot.i18n.Money.plain(total));
                store.appendCashEntry(entry);
            } else {
                creditService.add(who, contact, total, I18n.t("buy.entry.creditReason", units), reference);
                balance = creditService.balance(who);
                entry.setName(I18n.t("buy.entry.credit", who, units, com.tcg.bot.i18n.Money.symbol() + " " + CreditService.ars(total)));
                entry.setTotal("0");
                store.appendCashEntry(entry);
            }
            return new Result(units, total, payment, balance, waitingQuietly(lines));
        }
    }
}
