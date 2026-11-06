package com.tcg.bot.controller;

import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackLinkTest {

    @Test
    void buildsMailtoWithVersionAndSystemInfo() {
        String link = FeedbackModelAdvice.mailto("Sugerencia & bug (1.2.0)", "Contanos qué pasó:",
                "1.2.0", "es", "Windows 11 10.0", "17.0.9");

        assertThat(link).startsWith("mailto:tcgstorehelper@gmail.com?subject=");
        assertThat(link).doesNotContain("+").doesNotContain(" ");

        String query = link.substring(link.indexOf('?') + 1);
        String subject = query.substring("subject=".length(), query.indexOf("&body="));
        String body = query.substring(query.indexOf("&body=") + "&body=".length());
        assertThat(URLDecoder.decode(subject, StandardCharsets.UTF_8)).isEqualTo("Sugerencia & bug (1.2.0)");
        assertThat(URLDecoder.decode(body, StandardCharsets.UTF_8))
                .startsWith("Contanos qué pasó:")
                .contains("TCG Store Helper 1.2.0", "OS: Windows 11 10.0", "Java: 17.0.9", "Lang: es");
    }
}
