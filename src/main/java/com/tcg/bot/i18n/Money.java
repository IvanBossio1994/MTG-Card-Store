package com.tcg.bot.i18n;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Montos en la moneda de la tienda (Configuracion), con los separadores del idioma de la pantalla.
 * Las monedas con centavos de uso diario (USD, EUR, BRL...) usan 2 decimales; las demas
 * (ARS, CLP, JPY...) siguen en enteros como siempre.
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

    /** Monedas que se cobran con centavos. El resto se redondea a enteros. */
    private static final java.util.Set<String> WITH_CENTS = java.util.Set.of("USD", "EUR", "BRL", "MXN", "PEN", "GBP", "CAD", "AUD");

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

    /** Decimales de la moneda de la tienda: 2 para USD, EUR..., 0 para ARS, CLP, JPY... */
    public static int decimals() {
        return decimals(currency);
    }

    public static int decimals(String code) {
        return WITH_CENTS.contains(normalize(code)) ? 2 : 0;
    }

    /** Redondea un monto a los decimales de la moneda (4.499 -> 4.5 en USD, 1499.6 -> 1500 en ARS). */
    public static double round(double amount) {
        return java.math.BigDecimal.valueOf(amount)
                .setScale(decimals(), java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** True si el monto redondeado a la moneda es cero. */
    public static boolean isZero(double amount) {
        return round(amount) == 0;
    }

    /** Monto para guardar en el Sheet como texto: "1500" o "4.50" (siempre con punto). */
    public static String plain(double amount) {
        return String.format(Locale.US, "%." + decimals() + "f", round(amount));
    }

    /** Numero para un campo de formulario, sin ceros de mas: 500.0 -> "500", 0.25 -> "0.25". */
    public static String plainNumber(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** Monto para guardar en el Sheet como numero: entero o con decimales segun la moneda. */
    public static Number sheetValue(double amount) {
        double rounded = round(amount);
        return decimals() == 0 ? (Number) Math.round(rounded) : (Number) rounded;
    }

    /** "1.500" o "1,500" segun el idioma, sin simbolo; "4,50" o "4.50" si la moneda usa centavos. */
    public static String number(double amount) {
        int digits = decimals();
        NumberFormat format = NumberFormat.getNumberInstance(I18n.locale());
        format.setMinimumFractionDigits(digits);
        format.setMaximumFractionDigits(digits);
        format.setRoundingMode(java.math.RoundingMode.HALF_UP);
        return format.format(round(amount));
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
