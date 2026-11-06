package com.tcg.bot.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Colores de acento sacados del logo de la tienda. El color lo calcula el navegador al cargar el logo;
 * acá solo se valida y se oscurece lo necesario para que el texto blanco de los botones se siga leyendo.
 */
public final class StoreTheme {

    /** Contraste mínimo de texto blanco sobre el acento (WCAG AA para texto normal). */
    static final double MIN_CONTRAST_WITH_WHITE = 4.5;

    private static final Pattern HEX = Pattern.compile("#?[0-9a-fA-F]{6}");

    private StoreTheme() {
    }

    /** "#rrggbb" en minúscula, o "" si no es un color válido. */
    public static String normalizeHex(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (!HEX.matcher(trimmed).matches()) {
            return "";
        }
        return "#" + (trimmed.startsWith("#") ? trimmed.substring(1) : trimmed).toLowerCase(Locale.ROOT);
    }

    /** El mismo tono, oscurecido hasta que el texto blanco encima tenga buen contraste. "" si no es válido. */
    public static String readableAccent(String value) {
        String hex = normalizeHex(value);
        if (hex.isEmpty()) {
            return "";
        }
        double[] hsl = toHsl(hex);
        String result = hex;
        while (contrastWithWhite(result) < MIN_CONTRAST_WITH_WHITE && hsl[2] > 0) {
            hsl[2] = Math.max(0, hsl[2] - 0.02);
            result = fromHsl(hsl);
        }
        return result;
    }

    /** Segundo color del degradé: el tono corrido un poco, también legible con texto blanco. */
    public static String secondAccent(String value) {
        String accent = readableAccent(value);
        if (accent.isEmpty()) {
            return "";
        }
        double[] hsl = toHsl(accent);
        hsl[0] = (hsl[0] + 28) % 360;
        return readableAccent(fromHsl(hsl));
    }

    static double contrastWithWhite(String hex) {
        return 1.05 / (relativeLuminance(hex) + 0.05);
    }

    private static double relativeLuminance(String hex) {
        int[] rgb = rgb(hex);
        return 0.2126 * linear(rgb[0]) + 0.7152 * linear(rgb[1]) + 0.0722 * linear(rgb[2]);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int[] rgb(String hex) {
        int value = Integer.parseInt(hex.substring(1), 16);
        return new int[]{(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff};
    }

    private static double[] toHsl(String hex) {
        int[] rgb = rgb(hex);
        double r = rgb[0] / 255.0;
        double g = rgb[1] / 255.0;
        double b = rgb[2] / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2;
        double h = 0;
        double s = 0;
        if (max != min) {
            double d = max - min;
            s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
            if (max == r) {
                h = ((g - b) / d + (g < b ? 6 : 0)) * 60;
            } else if (max == g) {
                h = ((b - r) / d + 2) * 60;
            } else {
                h = ((r - g) / d + 4) * 60;
            }
        }
        return new double[]{h, s, l};
    }

    private static String fromHsl(double[] hsl) {
        double h = hsl[0] / 360;
        double s = hsl[1];
        double l = hsl[2];
        double r;
        double g;
        double b;
        if (s == 0) {
            r = g = b = l;
        } else {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            r = hue(p, q, h + 1.0 / 3);
            g = hue(p, q, h);
            b = hue(p, q, h - 1.0 / 3);
        }
        return String.format(Locale.ROOT, "#%02x%02x%02x",
                Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
    }

    private static double hue(double p, double q, double t) {
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1.0 / 6) {
            return p + (q - p) * 6 * t;
        }
        if (t < 1.0 / 2) {
            return q;
        }
        if (t < 2.0 / 3) {
            return p + (q - p) * (2.0 / 3 - t) * 6;
        }
        return p;
    }
}
