package com.tcg.bot.i18n;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Montos en la moneda de la tienda (Configuracion), con los separadores del idioma de la pantalla.
 * Los precios de la tienda son enteros: se redondean igual que siempre.
 */
public final class Money {

    public static final String DEFAULT_CURRENCY = "ARS";

    /** Monedas que se pueden elegir y su simbolo corto. */
    public static final Map<String, String> SYMBOLS = new java.util.LinkedHashMap<>();

    static {
        SYMBOLS.put("ARS", "$");
        SYMBOLS.put("USD", "US$");
        SYMBOLS.put("EUR", "€");
        SYMBOLS.put("BRL", "R$");
        SYMBOLS.put("MXN", "MX$");
        SYMBOLS.put("CLP", "CLP$");
        SYMBOLS.put("COP", "COL$");
        SYMBOLS.put("UYU", "$U");
        SYMBOLS.put("PEN", "S/");
        SYMBOLS.put("GBP", "£");
        SYMBOLS.put("CAD", "CA$");
        SYMBOLS.put("AUD", "A$");
        SYMBOLS.put("JPY", "¥");
    }

    private static volatile String currency = DEFAULT_CURRENCY;

    private Money() {
    }

    public static String currency() {
        return currency;
    }

    public static void setCurrency(String code) {
        currency = normalize(code);
    }

    public static String normalize(String code) {
        String value = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return SYMBOLS.containsKey(value) ? value : DEFAULT_CURRENCY;
    }

    public static String symbol() {
        return SYMBOLS.getOrDefault(currency, "$");
    }

    /** "1.500" o "1,500" segun el idioma, sin simbolo. */
    public static String number(double amount) {
        return NumberFormat.getIntegerInstance(I18n.locale()).format(Math.round(amount));
    }

    /** "2,49" o "2.49" segun el idioma. */
    public static String decimal(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(I18n.locale());
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount);
    }

    /** "$ 1.500", "US$ 1,500", "€ 1.500"... */
    public static String format(double amount) {
        return symbol() + " " + number(amount);
    }

    /** Precio de referencia en dolares, con centavos: "US$ 2,49" o "US$ 2.49". */
    public static String usd(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(I18n.locale());
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "US$ " + format.format(amount);
    }

    public static String eur(double amount) {
        NumberFormat format = NumberFormat.getNumberInstance(I18n.locale());
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "€ " + format.format(amount);
    }
}
