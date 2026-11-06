package com.tcg.bot.i18n;

import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Idioma actual, lista de idiomas, moneda y textos de app.js disponibles en todas las pantallas. */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
public class I18nModelAdvice {

    private final MessageSource messageSource;
    private final List<String> jsKeys;

    public I18nModelAdvice(MessageSource messageSource) {
        this.messageSource = messageSource;
        this.jsKeys = loadJsKeys();
    }

    @ModelAttribute("languages")
    public List<Languages.Language> languages() {
        return Languages.SUPPORTED;
    }

    @ModelAttribute("lang")
    public String lang() {
        return I18n.locale().getLanguage();
    }

    private static final MoneyView MONEY_VIEW = new MoneyView();

    @ModelAttribute("money")
    public MoneyView money() {
        return MONEY_VIEW;
    }

    @ModelAttribute("currencyDecimals")
    public int currencyDecimals() {
        return Money.decimals();
    }

    @ModelAttribute("currencySymbol")
    public String currencySymbol() {
        return Money.symbol();
    }

    @ModelAttribute("currencyCode")
    public String currencyCode() {
        return Money.currency();
    }

    @ModelAttribute("jsMessages")
    public Map<String, String> jsMessages() {
        Locale locale = I18n.locale();
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : jsKeys) {
            result.put(key.substring(3), messageSource.getMessage(key, null, key, locale));
        }
        return result;
    }

    private static List<String> loadJsKeys() {
        Properties properties = new Properties();
        try (InputStream input = I18nModelAdvice.class.getResourceAsStream("/messages.properties")) {
            if (input != null) {
                properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // Sin textos de JS: app.js muestra la clave.
        }
        List<String> keys = new ArrayList<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("js.")) {
                keys.add(key);
            }
        }
        keys.sort(String::compareTo);
        return keys;
    }
}
