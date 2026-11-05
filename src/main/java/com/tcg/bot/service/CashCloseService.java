package com.tcg.bot.service;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Cierre de caja del día: lo que la app espera que haya (ventas menos anulaciones) contra lo que se contó.
 * El cierre queda como una fila CIERRE en la hoja Caja, con total 0 para no cambiar ninguna suma.
 */
@Service
public class CashCloseService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final InventoryStore store;
    private final StockOperationsService stockOperations;

    public CashCloseService(InventoryStore store, StockOperationsService stockOperations) {
        this.store = store;
        this.stockOperations = stockOperations;
    }

    public record Day(
            String date,
            String formattedDate,
            List<CashRegisterEntry> sales,
            int salesCount,
            int units,
            String grossSales,
            String voids,
            String purchases,
            String creditUsed,
            String expected,
            double expectedValue,
            List<CashRegisterEntry> closes
    ) {
        public CashRegisterEntry lastClose() {
            return closes.isEmpty() ? null : closes.get(closes.size() - 1);
        }
    }

    public String today() {
        return LocalDate.now(stockOperations.clock).format(DATE_FORMAT);
    }

    public Day day(String date) throws Exception {
        String target = date == null || date.isBlank() ? today() : date.trim();
        List<CashRegisterEntry> entries = store.getCashRegisterEntries().stream()
                .filter(entry -> target.equals(entry.getDate()))
                .toList();

        List<CashRegisterEntry> moves = entries.stream()
                .filter(entry -> entry.affectsCash() || entry.isBuy() || entry.isCreditUse())
                .toList();
        double gross = moves.stream().filter(CashRegisterEntry::isSale).mapToDouble(CashRegisterEntry::signedTotal).sum();
        double voids = moves.stream().filter(CashRegisterEntry::isVoid).mapToDouble(CashRegisterEntry::signedTotal).sum();
        double purchases = moves.stream().filter(CashRegisterEntry::isBuy).mapToDouble(CashRegisterEntry::drawerAmount).sum();
        double credit = moves.stream().filter(CashRegisterEntry::isCreditUse).mapToDouble(CashRegisterEntry::drawerAmount).sum();
        double expected = moves.stream().mapToDouble(CashRegisterEntry::drawerAmount).sum();
        int units = moves.stream().filter(CashRegisterEntry::affectsCash).mapToInt(CashRegisterEntry::signedQuantity).sum();
        int count = (int) moves.stream().filter(entry -> entry.isSale() && !entry.isVoided()).count();

        return new Day(target, formatDate(target), moves, count, units, ars(gross), ars(Math.abs(voids)),
                ars(Math.abs(purchases)), ars(Math.abs(credit)), signedAmount(expected), expected,
                entries.stream().filter(CashRegisterEntry::isClose).toList());
    }

    /** Guarda el cierre. Los montos contados se separan por medio de pago porque en la tienda conviven. */
    public double close(String date, double cash, double transfers, double cards, String note) throws Exception {
        if (cash < 0 || transfers < 0 || cards < 0) {
            throw new StockOperationException(I18n.t("close.err.negative"));
        }

        synchronized (StockOperationsService.writeLock()) {
            Day day = day(date);
            double counted = cash + transfers + cards;
            double difference = counted - day.expectedValue();

            StringBuilder detail = new StringBuilder("Cierre de caja: contado $ ").append(ars(counted))
                    .append(" (efectivo $ ").append(ars(cash))
                    .append(", transferencias $ ").append(ars(transfers))
                    .append(", tarjeta $ ").append(ars(cards)).append(")")
                    .append(", esperado $ ").append(day.expected())
                    .append(", diferencia ").append(signed(difference));
            if (note != null && !note.isBlank()) {
                detail.append(". ").append(note.trim());
            }

            LocalDateTime now = LocalDateTime.now(stockOperations.clock);
            CashRegisterEntry entry = new CashRegisterEntry();
            entry.setDate(day.date());
            entry.setTime(now.format(TIME_FORMAT));
            entry.setType(CashRegisterEntry.TYPE_CLOSE);
            entry.setName(detail.toString());
            entry.setQuantity("0");
            entry.setTotal("0");
            store.appendCashEntry(entry);
            return difference;
        }
    }

    private static String signedAmount(double value) {
        return value < 0 ? "-" + ars(Math.abs(value)) : ars(value);
    }

    public static String signed(double value) {
        long rounded = Math.round(value);
        if (rounded == 0) {
            return com.tcg.bot.i18n.Money.symbol() + " " + "0";
        }
        return (rounded > 0 ? "+" : "-") + com.tcg.bot.i18n.Money.symbol() + " " + ars(Math.abs(rounded));
    }

    static String ars(double value) {
        return com.tcg.bot.i18n.Money.number(Math.round(value));
    }

    private static String formatDate(String date) {
        try {
            return LocalDate.parse(date, DATE_FORMAT).format(com.tcg.bot.i18n.Dates.shortDate());
        } catch (RuntimeException e) {
            return date;
        }
    }
}
