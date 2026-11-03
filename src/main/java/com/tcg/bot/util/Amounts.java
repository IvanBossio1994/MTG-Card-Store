package com.tcg.bot.util;

import java.util.regex.Pattern;

/**
 * Lectura tolerante de montos y cantidades que vienen del Sheet.
 * <p>
 * El Sheet puede devolver "1500", "1.500" (es-AR), "1,500" (en-US), "$ 1.500,50",
 * "1500.0" o "1.5E3" segun el formato de la celda. Antes "1.500" se leia como 1,5.
 */
public final class Amounts {

    private static final Pattern THOUSANDS_DOT = Pattern.compile("^-?\\d{1,3}(\\.\\d{3})+$");
    private static final Pattern THOUSANDS_COMMA = Pattern.compile("^-?\\d{1,3}(,\\d{3})+$");
    private static final Pattern SCIENTIFIC = Pattern.compile("^-?\\d+(\\.\\d+)?[eE][+-]?\\d+$");

    private Amounts() {
    }

    public static double parse(String value) {
        Double parsed = parseOrNull(value);
        return parsed == null ? 0 : parsed;
    }

    public static Double parseOrNull(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim()
                .replace("$", "")
                .replace("ARS", "")
                .replace("USD", "")
                .replace(" ", "")
                .replace(" ", "");

        if (normalized.isEmpty()) {
            return null;
        }

        if (!SCIENTIFIC.matcher(normalized).matches()) {
            int lastDot = normalized.lastIndexOf('.');
            int lastComma = normalized.lastIndexOf(',');

            if (lastDot >= 0 && lastComma >= 0) {
                normalized = lastComma > lastDot
                        ? normalized.replace(".", "").replace(",", ".")
                        : normalized.replace(",", "");
            } else if (lastDot >= 0) {
                if (THOUSANDS_DOT.matcher(normalized).matches()) {
                    normalized = normalized.replace(".", "");
                }
            } else if (lastComma >= 0) {
                normalized = THOUSANDS_COMMA.matcher(normalized).matches()
                        ? normalized.replace(",", "")
                        : normalized.replace(",", ".");
            }
        }

        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int parseInt(String value) {
        return (int) Math.round(parse(value));
    }
}
