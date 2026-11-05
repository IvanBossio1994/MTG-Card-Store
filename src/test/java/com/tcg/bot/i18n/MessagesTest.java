package com.tcg.bot.i18n;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Todos los idiomas tienen las mismas claves, los mismos {0} y textos que MessageFormat puede leer. */
class MessagesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)");
    private static final Pattern LONE_QUOTE = Pattern.compile("(?<!')'(?!')");

    @Test
    void everyLanguageHasTheSameKeysAndPlaceholders() throws Exception {
        Properties base = load("/messages.properties");
        assertThat(base).isNotEmpty();

        for (Languages.Language language : Languages.SUPPORTED) {
            if (language.code().equals("en")) {
                continue;
            }
            Properties translated = load("/messages_" + language.code() + ".properties");
            assertThat(translated.stringPropertyNames())
                    .as("claves de " + language.code())
                    .containsExactlyInAnyOrderElementsOf(base.stringPropertyNames());

            for (String key : base.stringPropertyNames()) {
                assertThat(placeholders(translated.getProperty(key)))
                        .as(language.code() + " " + key)
                        .isEqualTo(placeholders(base.getProperty(key)));
            }
        }
    }

    @Test
    void everyTextIsValidMessageFormat() throws Exception {
        for (Languages.Language language : Languages.SUPPORTED) {
            String file = language.code().equals("en") ? "/messages.properties" : "/messages_" + language.code() + ".properties";
            Properties properties = load(file);
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                assertThat(LONE_QUOTE.matcher(value).find())
                        .as(file + " " + key + ": usar '' o ’ en vez de una comilla simple")
                        .isFalse();
                new MessageFormat(value, language.locale()).format(new Object[]{1, 2, 3});
            }
        }
    }

    @Test
    void choicePluralsWork() {
        assertThat(new MessageFormat("{0,choice,0#{0} cartas|1#1 carta|1<{0} cartas}", Locale.forLanguageTag("es"))
                .format(new Object[]{1})).isEqualTo("1 carta");
    }

    private static Set<String> placeholders(String value) {
        Set<String> result = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value == null ? "" : value);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private static Properties load(String path) throws Exception {
        Properties properties = new Properties();
        try (InputStream input = MessagesTest.class.getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
