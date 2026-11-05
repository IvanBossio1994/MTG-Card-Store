package com.tcg.bot.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Getter
@AllArgsConstructor
public class InventoryMovement {

    private static final DateTimeFormatter SHEET_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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

    public String getFormattedDate() {
        if (date == null || date.isBlank()) {
            return "";
        }

        try {
            return LocalDate.parse(date, SHEET_DATE_FORMAT).format(com.tcg.bot.i18n.Dates.shortDate());
        } catch (RuntimeException e) {
            return date;
        }
    }
}
