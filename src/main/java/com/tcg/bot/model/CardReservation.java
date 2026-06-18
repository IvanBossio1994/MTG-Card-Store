package com.tcg.bot.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CardReservation {

    public static final String STATUS_IN_STOCK = "En Stock";
    public static final String STATUS_WANTED = "Sin Stock";
    public static final String STATUS_RESERVED = "Reservada";

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
    private int rowIndex;
    private int inventoryRowIndex;
    private int availableStock;
    private String localPrice;
    private String formattedLocalPrice;
    private double lineTotalPrice;
    private String formattedLineTotalPrice;
    private double deliverableTotalPrice;
    private String formattedDeliverableTotalPrice;
    private List<Integer> sourceRowIndexes = new ArrayList<>();
    private List<ReservationConditionStock> conditionStocks = new ArrayList<>();

    public List<Integer> effectiveRowIndexes() {
        if (sourceRowIndexes == null || sourceRowIndexes.isEmpty()) {
            return rowIndex > 0 ? List.of(rowIndex) : List.of();
        }

        return sourceRowIndexes;
    }

    public String getFormattedReservationDate() {
        return formatDateTime(reservationDate);
    }

    public boolean isDeliverable() {
        return inventoryRowIndex > 0 && availableStock > 0;
    }

    public boolean isPartialDelivery() {
        return isDeliverable() && availableStock < reservationQuantity();
    }

    public String getDeliveryButtonLabel() {
        return isPartialDelivery() ? "Entrega parcial" : "Entregar";
    }

    public int reservationQuantity() {
        if (quantity == null || quantity.isBlank()) {
            return 1;
        }

        try {
            return Math.max(1, Integer.parseInt(quantity.trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
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
