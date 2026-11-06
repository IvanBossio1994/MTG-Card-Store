package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.InventoryMovement;
import com.tcg.bot.model.Reservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reportes simples para decidir qué comprar y qué liquidar: lo más vendido, lo que no se mueve,
 * cuánto vale el stock (a precio y a costo) y quiénes compran más. Todo sale de las pestañas del Sheet.
 */
@Service
public class ReportService {

    /** Una carta con stock y sin ventas ni ingresos en estos días se considera "quieta". */
    public static final int STILL_DAYS = 90;
    static final int TOP = 10;
    static final int STILL_LIMIT = 20;

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final InventoryStore store;
    private final StockOperationsService stockOperations;

    public ReportService(InventoryStore store, StockOperationsService stockOperations) {
        this.store = store;
        this.stockOperations = stockOperations;
    }

    public record TopCard(String name, String set, int units, double revenue, Double profit) {
    }

    public record StillCard(InventoryCard card, int units, double value, String lastActivity) {
    }

    public record Customer(String name, int orders, double total) {
    }

    public record StockValue(double atPrice, double atCost, double priceOfCosted, int unitsWithoutCost, int units) {
        /** Lo que se ganaría vendiendo a precio todo lo que tiene costo cargado. */
        public double potentialProfit() {
            return priceOfCosted - atCost;
        }
    }

    public record Report(
            int days,
            double sales,
            int units,
            int salesCount,
            CashCloseService.Profit profit,
            List<TopCard> topCards,
            StockValue stock,
            List<StillCard> still,
            int stillTotal,
            List<Customer> customers
    ) {
    }

    public Report report(int days) throws Exception {
        int period = Math.max(1, Math.min(days, 3650));
        LocalDate today = LocalDate.now(stockOperations.clock);
        String since = today.minusDays(period - 1L).toString();
        String stillSince = today.minusDays(STILL_DAYS - 1L).toString();

        List<CashRegisterEntry> cash = store.getCashRegisterEntries();
        List<CashRegisterEntry> sales = cash.stream()
                .filter(entry -> entry.isSale() && !entry.isVoided() && after(entry.getDate(), since))
                .toList();

        double total = sales.stream().mapToDouble(CashRegisterEntry::signedTotal).sum();
        int units = sales.stream().mapToInt(CashRegisterEntry::signedQuantity).sum();

        List<InventoryCard> stock = store.getAllStockCards().stream()
                .filter(card -> card.getName() != null && !card.getName().isBlank())
                .filter(card -> StockOperationsService.quantity(card) > 0)
                .toList();

        List<StillCard> still = stillCards(stock, cash, stillSince);
        return new Report(period, total, units, sales.size(), CashCloseService.Profit.of(sales),
                topCards(sales), stockValue(stock), still.size() > STILL_LIMIT ? still.subList(0, STILL_LIMIT) : still,
                still.size(), customers(since));
    }

    private static List<TopCard> topCards(List<CashRegisterEntry> sales) {
        Map<String, List<CashRegisterEntry>> byCard = new LinkedHashMap<>();
        for (CashRegisterEntry sale : sales) {
            byCard.computeIfAbsent(cardKey(sale), key -> new ArrayList<>()).add(sale);
        }
        List<TopCard> top = new ArrayList<>();
        for (List<CashRegisterEntry> group : byCard.values()) {
            CashRegisterEntry first = group.get(0);
            CashCloseService.Profit profit = CashCloseService.Profit.of(group);
            top.add(new TopCard(first.getName(), first.getSetName(),
                    group.stream().mapToInt(CashRegisterEntry::signedQuantity).sum(),
                    group.stream().mapToDouble(CashRegisterEntry::signedTotal).sum(),
                    profit.known() && profit.withoutCost() == 0 ? profit.value() : null));
        }
        top.sort(Comparator.comparingInt(TopCard::units).thenComparingDouble(TopCard::revenue).reversed());
        return top.size() > TOP ? top.subList(0, TOP) : top;
    }

    private static StockValue stockValue(List<InventoryCard> stock) {
        double atPrice = 0;
        double atCost = 0;
        double priceOfCosted = 0;
        int withoutCost = 0;
        int units = 0;
        for (InventoryCard card : stock) {
            int quantity = StockOperationsService.quantity(card);
            double value = card.localPriceValue() * quantity;
            units += quantity;
            atPrice += value;
            Double cost = card.costValue();
            if (cost == null) {
                withoutCost += quantity;
            } else {
                atCost += cost * quantity;
                priceOfCosted += value;
            }
        }
        return new StockValue(atPrice, atCost, priceOfCosted, withoutCost, units);
    }

    /**
     * Cartas con stock que no se vendieron ni entraron en los últimos {@link #STILL_DAYS} días.
     * Lo que entró hace poco no cuenta: todavía no tuvo tiempo de venderse.
     */
    private List<StillCard> stillCards(List<InventoryCard> stock, List<CashRegisterEntry> cash, String since) {
        Map<String, String> lastSale = new HashMap<>();
        for (CashRegisterEntry entry : cash) {
            if (entry.isSale()) {
                lastSale.merge(cardKey(entry), safe(entry.getDate()), ReportService::latest);
            }
        }
        Map<String, String> lastEntry = new HashMap<>();
        try {
            for (InventoryMovement movement : store.getRecentMovements()) {
                if (com.tcg.bot.util.Amounts.parseInt(movement.getQuantity()) > 0) {
                    lastEntry.merge(movementKey(movement), safe(movement.getDate()), ReportService::latest);
                }
            }
        } catch (Exception e) {
            log.debug("Reportes: no se pudieron leer los movimientos: {}", e.getMessage());
        }

        List<StillCard> still = new ArrayList<>();
        for (InventoryCard card : stock) {
            String sold = card.getId() == null ? "" : lastSale.getOrDefault("id:" + card.getId(), "");
            sold = latest(sold, lastSale.getOrDefault(printKey(card.getName(), card.getSetName(), card.getCollectorNumber()), ""));
            String entered = lastEntry.getOrDefault(printKey(card.getName(), card.getSetName(), card.getCollectorNumber()), "");
            String last = latest(sold, entered);
            if (!last.isEmpty() && after(last, since)) {
                continue;
            }
            int quantity = StockOperationsService.quantity(card);
            still.add(new StillCard(card, quantity, card.localPriceValue() * quantity, last));
        }
        still.sort(Comparator.comparingDouble(StillCard::value).reversed());
        return still;
    }

    private List<Customer> customers(String since) {
        Map<String, double[]> totals = new LinkedHashMap<>();
        Map<String, String> names = new HashMap<>();
        try {
            Map<String, java.util.Set<String>> orders = new HashMap<>();
            for (Reservation line : store.getReservations()) {
                if (!Reservation.STATUS_PICKED_UP.equalsIgnoreCase(line.getStatus()) || !after(line.getDate(), since)
                        || line.getCustomer() == null || line.getCustomer().isBlank()) {
                    continue;
                }
                String key = line.getCustomer().trim().toLowerCase(Locale.ROOT);
                names.putIfAbsent(key, line.getCustomer().trim());
                totals.computeIfAbsent(key, k -> new double[1])[0] += line.total();
                orders.computeIfAbsent(key, k -> new java.util.HashSet<>()).add(line.getReservationId());
            }
            List<Customer> customers = new ArrayList<>();
            for (var entry : totals.entrySet()) {
                customers.add(new Customer(names.get(entry.getKey()), orders.get(entry.getKey()).size(), entry.getValue()[0]));
            }
            customers.sort(Comparator.comparingDouble(Customer::total).reversed());
            return customers.size() > TOP ? customers.subList(0, TOP) : customers;
        } catch (Exception e) {
            log.debug("Reportes: no se pudieron leer las reservas: {}", e.getMessage());
            return List.of();
        }
    }

    /** Por ID de carta; las ventas viejas que no lo guardaban, por nombre, edición y número. */
    private static String cardKey(CashRegisterEntry sale) {
        return sale.getCardId() != null && !sale.getCardId().isBlank()
                ? "id:" + sale.getCardId()
                : printKey(sale.getName(), sale.getSetName(), sale.getCollectorNumber());
    }

    private static String movementKey(InventoryMovement movement) {
        return printKey(movement.getName(), movement.getSetName(), movement.getCollectorNumber());
    }

    private static String printKey(String name, String set, String number) {
        return safe(name).trim().toLowerCase(Locale.ROOT) + "|" + safe(set).trim().toLowerCase(Locale.ROOT)
                + "|" + safe(number).trim().replaceFirst("^0+(?=\\d)", "");
    }

    private static boolean after(String date, String since) {
        return date != null && !date.isBlank() && date.compareTo(since) >= 0;
    }

    private static String latest(String first, String second) {
        return safe(first).compareTo(safe(second)) >= 0 ? safe(first) : safe(second);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
