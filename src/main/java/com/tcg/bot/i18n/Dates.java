package com.tcg.bot.i18n;

import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.Locale;

/** Fechas cortas en el formato del idioma de la pantalla, siempre con el año completo (05/11/2026, 11/5/2026, 2026/11/05...). */
public final class Dates {

    private Dates() {
    }

    public static DateTimeFormatter shortDate() {
        Locale locale = I18n.locale();
        String pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale);
        if (!pattern.contains("yyyy")) {
            pattern = pattern.replaceAll("y+", "yyyy");
        }
        if ("es".equals(locale.getLanguage()) || "pt".equals(locale.getLanguage())
                || "it".equals(locale.getLanguage()) || "fr".equals(locale.getLanguage())) {
            pattern = "dd/MM/yyyy";
        }
        return DateTimeFormatter.ofPattern(pattern, locale);
    }
}
