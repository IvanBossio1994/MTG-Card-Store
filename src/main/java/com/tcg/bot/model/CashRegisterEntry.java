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

    private static final DateTimeFormatter SHEET_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
            return LocalDate.parse(date, SHEET_DATE_FORMAT).format(com.tcg.bot.i18n.Dates.shortDate());
        } catch (RuntimeException e) {
            return date;
        }
    }

    public static final String TYPE_SALE = "VENTA";
    public static final String TYPE_VOID = "ANULACION";
    /** Cierre de caja: solo deja anotado lo contado; no suma ni resta. */
    public static final String TYPE_CLOSE = "CIERRE";
    /** Compra de cartas a un cliente. Pagada en efectivo resta del cajón; con crédito de tienda no. */
    public static final String TYPE_BUY = "COMPRA";
    /** Parte de una venta pagada con crédito de tienda: esa plata no entra al cajón. */
    public static final String TYPE_CREDIT = "CREDITO";

    public boolean isSale() {
        return TYPE_SALE.equalsIgnoreCase(type);
    }

    public boolean isBuy() {
        return TYPE_BUY.equalsIgnoreCase(type);
    }

    public boolean isCreditUse() {
        return TYPE_CREDIT.equalsIgnoreCase(type);
    }

    /**
     * Lo que mueve el efectivo/medios del cajón: ventas, anulaciones, compras pagadas (no con crédito) y la parte
     * de ventas pagada con crédito (que se resta porque no entró plata).
     */
    public double drawerAmount() {
        if (affectsCash()) {
            return signedTotal();
        }
        if (isBuy() || isCreditUse()) {
            return -Math.abs(Amounts.parse(total));
        }
        return 0;
    }

    public boolean isClose() {
        return TYPE_CLOSE.equalsIgnoreCase(type);
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
        return com.tcg.bot.i18n.Money.number(signedTotal());
    }

    public boolean canBeVoided() {
        return isSale() && saleId != null && !saleId.isBlank() && !voided;
    }

    private String formatAmount(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return com.tcg.bot.i18n.Money.number(Amounts.parse(value));
    }
}
