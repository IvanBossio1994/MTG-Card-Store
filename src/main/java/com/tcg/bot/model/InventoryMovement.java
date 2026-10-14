package com.tcg.bot.model;

import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Getter
public class InventoryMovement {

    private static final DateTimeFormatter SHEET_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private String dateTime;

    private String date;

    private String time;

    private String type;

    private String quantity;

    private String name;

    private String setName;

    private String setCode;

    private String collectorNumber;

    private String printing;

    private String previousStock;

    private String newStock;

    private String source;

    private String condition;

    private String client;

    private String dni;

    private String reservationId;

    public InventoryMovement(
            String dateTime,
            String date,
            String time,
            String type,
            String quantity,
            String name,
            String setName,
            String setCode,
            String collectorNumber,
            String printing,
            String previousStock,
            String newStock,
            String source
    ) {
        this(
                dateTime,
                date,
                time,
                type,
                quantity,
                name,
                setName,
                setCode,
                collectorNumber,
                printing,
                previousStock,
                newStock,
                source,
                "",
                "",
                "",
                ""
        );
    }

    public InventoryMovement(
            String dateTime,
            String date,
            String time,
            String type,
            String quantity,
            String name,
            String setName,
            String setCode,
            String collectorNumber,
            String printing,
            String previousStock,
            String newStock,
            String source,
            String condition,
            String client,
            String dni,
            String reservationId
    ) {
        this.dateTime = dateTime;
        this.date = date;
        this.time = time;
        this.type = type;
        this.quantity = quantity;
        this.name = name;
        this.setName = setName;
        this.setCode = setCode;
        this.collectorNumber = collectorNumber;
        this.printing = printing;
        this.previousStock = previousStock;
        this.newStock = newStock;
        this.source = source;
        this.condition = condition;
        this.client = client;
        this.dni = dni;
        this.reservationId = reservationId;
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
}
