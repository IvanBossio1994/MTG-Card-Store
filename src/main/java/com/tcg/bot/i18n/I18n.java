package com.tcg.bot.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Textos traducidos desde cualquier clase: {@code I18n.t("clave", args...)}.
 * Usa el idioma de la pantalla que hizo el pedido.
 */
@Component
public class I18n {

    private static volatile MessageSource messages;

    public I18n(MessageSource messageSource) {
        messages = messageSource;
    }

    public static Locale locale() {
        return Languages.supportedOrFallback(LocaleContextHolder.getLocale());
    }

    public static String t(String key, Object... args) {
        MessageSource source = messages;
        if (source == null) {
            // Antes de que arranque Spring (o en tests sin Spring): los mismos archivos messages*.properties.
            source = Standalone.SOURCE;
        }
        return source.getMessage(key, args, key, locale());
    }

    /** Se crea recién la primera vez que hace falta, con la misma configuración que application.properties. */
    private static final class Standalone {
        static final MessageSource SOURCE = create();

        private static MessageSource create() {
            ResourceBundleMessageSource source = new ResourceBundleMessageSource();
            source.setBasename("messages");
            source.setDefaultEncoding("UTF-8");
            source.setFallbackToSystemLocale(false);
            source.setAlwaysUseMessageFormat(true);
            return source;
        }
    }
}
