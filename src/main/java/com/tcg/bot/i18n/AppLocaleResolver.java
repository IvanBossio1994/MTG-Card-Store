package com.tcg.bot.i18n;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.time.Duration;
import java.util.Collections;
import java.util.Locale;

/**
 * Idioma elegido en el menu (cookie). Si no eligieron, el del navegador; si no coincide con ninguno,
 * el del sistema; y si tampoco, ingles.
 */
public class AppLocaleResolver extends CookieLocaleResolver {

    public static final String COOKIE = "tcg-lang";

    public AppLocaleResolver() {
        super(COOKIE);
        setCookieMaxAge(Duration.ofDays(3650));
        setDefaultLocaleFunction(AppLocaleResolver::detect);
    }

    static Locale detect(HttpServletRequest request) {
        if (request.getHeader("Accept-Language") != null) {
            for (Locale candidate : Collections.list(request.getLocales())) {
                Locale match = Languages.supported(candidate);
                if (match != null) {
                    return match;
                }
            }
        }
        return Languages.supportedOrFallback(Locale.getDefault());
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        return Languages.supportedOrFallback(super.resolveLocale(request));
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        super.setLocale(request, response, locale == null ? null : Languages.supported(locale));
    }
}
