package com.tcg.bot.service;

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

    public record Result(int units, double total, Payment payment, double newCreditBalance) {
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

    public BuyService(InventoryStore store, StockOperationsService stockOperations, CreditService creditService) {
        this.store = store;
        this.stockOperations = stockOperations;
        this.creditService = creditService;
    }

    public Result buy(String customer, String contact, Payment payment, List<Line> lines) throws Exception {
        if (lines == null || lines.isEmpty()) {
            throw new StockOperationException("La compra no tiene cartas.");
        }
        if (payment == Payment.CREDIT && (customer == null || customer.isBlank())) {
            throw new StockOperationException("Para pagar con crédito poné el nombre del cliente.");
        }
        for (Line line : lines) {
            if (line.quantity() <= 0 || line.unitPaid() < 0) {
                throw new StockOperationException("Revisá cantidades y precios de " + line.card().getName() + ".");
            }
        }

        String who = customer == null || customer.isBlank() ? "cliente" : customer.trim();
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
                stockOperations.appendMovementQuietly(stockOperations.movement(
                        now, line.quantity(), card, previous, next, SOURCE_BOUGHT + " (" + who + ")"));
                units += line.quantity();
                total += Math.round(line.unitPaid()) * line.quantity();
            }

            CashRegisterEntry entry = new CashRegisterEntry();
            entry.setDate(now.toLocalDate().toString());
            entry.setTime(now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            entry.setType(CashRegisterEntry.TYPE_BUY);
            entry.setQuantity(String.valueOf(units));
            entry.setSaleId(reference);
            double balance = 0;
            if (payment == Payment.CASH) {
                entry.setName("Compra a " + who + " (" + units + (units == 1 ? " carta)" : " cartas)"));
                entry.setTotal(String.valueOf(Math.round(total)));
                store.appendCashEntry(entry);
            } else {
                creditService.add(who, contact, total, "Compra de " + units + (units == 1 ? " carta" : " cartas"), reference);
                balance = creditService.balance(who);
                entry.setName("Compra a " + who + " (" + units + (units == 1 ? " carta" : " cartas")
                        + ", pagada con $ " + CreditService.ars(total) + " de crédito)");
                entry.setTotal("0");
                store.appendCashEntry(entry);
            }
            return new Result(units, total, payment, balance);
        }
    }
}
