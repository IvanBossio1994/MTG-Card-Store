package com.tcg.bot.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@Setter
public class CardReservation {

    public static final String STATUS_WANTED = "BUSCADA";
    public static final String STATUS_RESERVED = "RESERVADA";
    public static final String STATUS_PAID = "PAGADA";
    public static final String STATUS_RETIRED = "RETIRADA";
    public static final String STATUS_CANCELLED = "CANCELADA";

    private static final DateTimeFormatter SHEET_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter SHEET_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private String id;
    private String status;
    private String name;
    private String setName;
    private String setCode;
    private String collectorNumber;
    private String printing;
    private String quantity;
    private String client;
    private String phone;
    private String dni;
    private String reservationDate;
    private String pickupDate;
    private String paymentDate;
    private String notes;

    public String getFormattedReservationDate() {
        return formatDateTime(reservationDate);
    }

    public String getFormattedPaymentDate() {
        return formatDateTime(paymentDate);
    }

    public String getFormattedPickupDate() {
        if (pickupDate == null || pickupDate.isBlank()) {
            return "";
        }

        if ("A convenir".equalsIgnoreCase(pickupDate.trim())) {
            return "A convenir";
        }

        try {
            return LocalDate.parse(pickupDate, SHEET_DATE_FORMAT).format(DISPLAY_DATE_FORMAT);
        } catch (RuntimeException e) {
            return pickupDate;
        }
    }

    private String formatDateTime(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        try {
            return LocalDateTime.parse(value, SHEET_DATE_TIME_FORMAT).format(DISPLAY_DATE_TIME_FORMAT);
        } catch (RuntimeException e) {
            return value;
        }
    }
}
