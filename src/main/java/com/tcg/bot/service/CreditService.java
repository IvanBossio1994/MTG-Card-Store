package com.tcg.bot.service;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.model.CreditEntry;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Crédito de tienda por cliente, anotado en la hoja "Credito" del Sheet. El saldo es la suma de sus filas. */
@Service
public class CreditService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final InventoryStore store;
    private final StockOperationsService stockOperations;

    public CreditService(InventoryStore store, StockOperationsService stockOperations) {
        this.store = store;
        this.stockOperations = stockOperations;
    }

    public record Account(String customer, String contact, double balance, List<CreditEntry> history) {
        public String formattedBalance() {
            return ars(balance);
        }
    }

    public List<Account> accounts() throws Exception {
        Map<String, List<CreditEntry>> byCustomer = new LinkedHashMap<>();
        for (CreditEntry entry : store.getCreditEntries()) {
            byCustomer.computeIfAbsent(entry.customerKey(), key -> new ArrayList<>()).add(entry);
        }
        List<Account> accounts = new ArrayList<>();
        for (List<CreditEntry> entries : byCustomer.values()) {
            String contact = entries.stream().map(CreditEntry::contact)
                    .filter(value -> value != null && !value.isBlank()).reduce((first, second) -> second).orElse("");
            double balance = entries.stream().mapToDouble(CreditEntry::amount).sum();
            accounts.add(new Account(entries.get(0).customer(), contact, balance, List.copyOf(entries)));
        }
        accounts.sort(Comparator.comparing(Account::balance).reversed()
                .thenComparing(Account::customer, String.CASE_INSENSITIVE_ORDER));
        return accounts;
    }

    public double balance(String customer) throws Exception {
        String key = CreditEntry.key(customer);
        return store.getCreditEntries().stream()
                .filter(entry -> entry.customerKey().equals(key))
                .mapToDouble(CreditEntry::amount)
                .sum();
    }

    /** Suma (positivo) o descuenta (negativo) saldo. Nunca deja el saldo en negativo. */
    public CreditEntry add(String customer, String contact, double amount, String reason, String reference) throws Exception {
        if (customer == null || customer.isBlank()) {
            throw new StockOperationException(I18n.t("err.customer.required"));
        }
        if (com.tcg.bot.i18n.Money.isZero(amount)) {
            throw new StockOperationException(I18n.t("cust.err.zero"));
        }
        synchronized (StockOperationsService.writeLock()) {
            if (amount < 0 && com.tcg.bot.i18n.Money.round(balance(customer) + amount) < 0) {
                throw new StockOperationException(I18n.t("cust.err.notEnough", customer.trim(), com.tcg.bot.i18n.Money.symbol() + " " + ars(balance(customer)),
                        com.tcg.bot.i18n.Money.symbol() + " " + ars(-amount)));
            }
            LocalDateTime now = LocalDateTime.now(stockOperations.clock);
            CreditEntry entry = new CreditEntry(now.format(DATE_FORMAT), now.format(TIME_FORMAT), customer.trim(),
                    contact == null ? "" : contact.trim(), com.tcg.bot.i18n.Money.round(amount), reason == null ? "" : reason.trim(),
                    reference == null ? "" : reference);
            store.appendCreditEntry(entry);
            return entry;
        }
    }

    public static String ars(double value) {
        return com.tcg.bot.i18n.Money.number(value);
    }
}
