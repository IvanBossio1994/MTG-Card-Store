package com.tcg.bot.model;

import com.tcg.bot.util.Amounts;

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
    private String saleId;
    private String voidsSaleId;
    private String cardId;
    private boolean voided;

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

    public static final String TYPE_SALE = "VENTA";
    public static final String TYPE_VOID = "ANULACION";

    public boolean isSale() {
        return TYPE_SALE.equalsIgnoreCase(type);
    }

    public boolean isVoid() {
        return TYPE_VOID.equalsIgnoreCase(type);
    }

    /** Venta o anulacion: lo que suma o resta en la caja. */
    public boolean affectsCash() {
        return isSale() || isVoid();
    }

    /** Total con signo: las anulaciones restan aunque en el Sheet esten escritas en positivo. */
    public double signedTotal() {
        double value = Math.abs(Amounts.parse(total));
        return isVoid() ? -value : value;
    }

    public int signedQuantity() {
        int value = Math.abs(Amounts.parseInt(quantity));
        return isVoid() ? -value : value;
    }

    public String getFormattedSignedTotal() {
        return ARS_FORMAT.format(signedTotal());
    }

    public boolean canBeVoided() {
        return isSale() && saleId != null && !saleId.isBlank() && !voided;
    }

    private String formatAmount(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return ARS_FORMAT.format(Amounts.parse(value));
    }
}
