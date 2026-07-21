package com.tcg.bot.service;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class WhatsappClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsappClient.class);

    private final StoreSettingsService storeSettingsService;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final String graphApiVersion;

    public WhatsappClient(
            StoreSettingsService storeSettingsService,
            @Value("${whatsapp.graph-api-version:v23.0}") String graphApiVersion
    ) {
        this.storeSettingsService = storeSettingsService;
        this.graphApiVersion = graphApiVersion;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void sendText(String to, String body) {
        if (!storeSettingsService.hasWhatsappConfigured()) {
            return;
        }

        String normalizedBody = body == null || body.isBlank()
                ? "No pude generar una respuesta en este momento."
                : body.trim();
        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "text",
                "text", Map.of(
                        "preview_url", false,
                        "body", truncate(normalizedBody, 4096)
                )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://graph.facebook.com/" + graphApiVersion + "/"
                        + storeSettingsService.getWhatsappPhoneNumberId() + "/messages"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + storeSettingsService.getWhatsappAccessToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return;
                }
                log.warn("WhatsApp respondio {} al enviar mensaje: {}", response.statusCode(), response.body());
            } catch (Exception e) {
                log.warn("No se pudo enviar mensaje de WhatsApp. Intento {}", attempt, e);
            }

            sleepBeforeRetry(attempt);
        }
    }

    public void sendTextLines(String to, List<String> lines) {
        sendText(to, String.join("\n", lines));
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }

    private void sleepBeforeRetry(int attempt) {
        if (attempt >= 3) {
            return;
        }

        try {
            Thread.sleep(400L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
