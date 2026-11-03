package com.tcg.bot.model;

import com.tcg.bot.util.Amounts;
import lombok.Getter;
import lombok.Setter;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Una linea de una reserva (pedido de un cliente). Varias lineas comparten el mismo reservationId. */
@Getter
@Setter
public class Reservation {

    public static final String STATUS_ACTIVE = "ACTIVA";
    public static final String STATUS_PICKED_UP = "RETIRADA";
    public static final String STATUS_CANCELLED = "CANCELADA";
    public static final String STATUS_EXPIRED = "VENCIDA";

    private static final DecimalFormat ARS_FORMAT = new DecimalFormat(
            "#,##0",
            DecimalFormatSymbols.getInstance(new Locale("es", "AR"))
    );
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private int rowIndex;
    private String date;
    private String time;
    private String reservationId;
    private String customer;
    private String contact;
    private String cardId;
    private String name;
    private String setName;
    private String setCode;
    private String collectorNumber;
    private String printing;
    private String quantity;
    private String unitPrice;
    private String expiresOn;
    private String status;
    private String updatedAt;

    public boolean isActive() {
        return STATUS_ACTIVE.equalsIgnoreCase(status);
    }

    public int units() {
        return Math.max(Amounts.parseInt(quantity), 0);
    }

    public double total() {
        return Amounts.parse(unitPrice) * units();
    }

    public boolean isOverdue(LocalDate today) {
        if (!isActive() || expiresOn == null || expiresOn.isBlank()) {
            return false;
        }

        try {
            return LocalDate.parse(expiresOn.trim()).isBefore(today);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public String getFormattedUnitPrice() {
        return ARS_FORMAT.format(Amounts.parse(unitPrice));
    }

    public String getFormattedTotal() {
        return ARS_FORMAT.format(total());
    }

    public String getFormattedExpiresOn() {
        return formatDate(expiresOn);
    }

    public String getFormattedDate() {
        return formatDate(date);
    }

    private static String formatDate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        try {
            return LocalDate.parse(value.trim()).format(DISPLAY_DATE);
        } catch (RuntimeException e) {
            return value;
        }
    }
}
