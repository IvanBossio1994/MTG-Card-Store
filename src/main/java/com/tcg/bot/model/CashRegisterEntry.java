package com.tcg.bot.model;

import lombok.Getter;
import lombok.Setter;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Getter
@Setter
public class CashRegisterEntry {

    private static final DecimalFormat ARS_FORMAT = new DecimalFormat(
            "#,##0",
            DecimalFormatSymbols.getInstance(new Locale("es", "AR"))
    );
    private static final DateTimeFormatter SHEET_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private String date;
    private String time;
    private String type;
    private String name;
    private String setName;
    private String setCode;
    private String collectorNumber;
    private String printing;
    private String localPrice;
    private String quantity;
    private String total;
    private String dayTotal;
    private String status;

    public String getFormattedLocalPrice() {
        return formatAmount(localPrice);
    }

    public String getFormattedTotal() {
        return formatAmount(total);
    }

    public String getFormattedDayTotal() {
        return formatAmount(dayTotal);
    }

    public String getFormattedDate() {
        if (date == null || date.isBlank()) {
            return "";
        }

        try {
            return LocalDate.parse(date, SHEET_DATE_FORMAT).format(DISPLAY_DATE_FORMAT);
        } catch (RuntimeException e) {
            return date;
        }
    }

    private String formatAmount(String value) {
        double number = parseAmount(value);

        if (number <= 0 && (value == null || value.isBlank())) {
            return "";
        }

        return ARS_FORMAT.format(number);
    }

    private double parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        String normalized = value.replace("$", "").trim();

        if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.replace(".", "").replace(",", ".");
        } else if (normalized.contains(",")) {
            normalized = normalized.replace(",", ".");
        }

        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
