package com.tcg.bot.i18n;

import java.util.List;
import java.util.Locale;

/** Idiomas de la app. El primero que coincide con el navegador o el sistema gana; si ninguno, ingles. */
public final class Languages {

    public record Language(String code, String nativeName) {
        public Locale locale() {
            return Locale.forLanguageTag(code);
        }
    }

    public static final List<Language> SUPPORTED = List.of(
            new Language("es", "Español"),
            new Language("en", "English"),
            new Language("pt", "Português"),
            new Language("fr", "Français"),
            new Language("de", "Deutsch"),
            new Language("it", "Italiano"),
            new Language("ja", "日本語")
    );

    public static final Locale FALLBACK = Locale.ENGLISH;

    private Languages() {
    }

    /** Devuelve el idioma soportado para ese locale (solo mira el idioma, no el pais), o null. */
    public static Locale supported(Locale locale) {
        if (locale == null) {
            return null;
        }
        String language = locale.getLanguage();
        for (Language candidate : SUPPORTED) {
            if (candidate.code().equals(language)) {
                return candidate.locale();
            }
        }
        return null;
    }

    public static Locale supportedOrFallback(Locale locale) {
        Locale match = supported(locale);
        return match == null ? FALLBACK : match;
    }
}
