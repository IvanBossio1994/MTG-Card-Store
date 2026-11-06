package com.tcg.bot.controller;

import com.tcg.bot.i18n.I18n;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Link "Reportar un problema o sugerir algo" del menu: abre el programa de mail de la persona con un mail ya armado
 * para la cuenta del proyecto, con la version de la app y del sistema al final. Sin servidor propio ni servicios externos.
 */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
public class FeedbackModelAdvice {

    public static final String FEEDBACK_EMAIL = "tcgstorehelper@gmail.com";

    private final MessageSource messageSource;
    private final String version;

    public FeedbackModelAdvice(MessageSource messageSource, @Value("${app.version:dev}") String version) {
        this.messageSource = messageSource;
        this.version = version == null || version.isBlank() ? "dev" : version.trim();
    }

    @ModelAttribute("feedbackEmail")
    public String feedbackEmail() {
        return FEEDBACK_EMAIL;
    }

    @ModelAttribute("feedbackMailto")
    public String feedbackMailto() {
        Locale locale = I18n.locale();
        String subject = messageSource.getMessage("feedback.mail.subject", new Object[]{version}, locale);
        String intro = messageSource.getMessage("feedback.mail.body", null, locale);
        return mailto(subject, intro, version, locale.getLanguage(),
                System.getProperty("os.name", "?") + " " + System.getProperty("os.version", ""),
                System.getProperty("java.version", "?"));
    }

    static String mailto(String subject, String intro, String version, String language, String os, String java) {
        String body = intro + "\n\n\n\n"
                + "---\n"
                + "TCG Store Helper " + version + "\n"
                + "OS: " + os.trim() + "\n"
                + "Java: " + java + "\n"
                + "Lang: " + language + "\n";
        return "mailto:" + FEEDBACK_EMAIL + "?subject=" + encode(subject) + "&body=" + encode(body);
    }

    /** Los programas de mail esperan %20 para los espacios y %0A para los saltos de linea (no "+"). */
    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
