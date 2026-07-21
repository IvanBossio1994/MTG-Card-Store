package com.tcg.bot.controller;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tcg.bot.service.StoreSettingsService;
import com.tcg.bot.service.WhatsappOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WhatsappWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsappWebhookController.class);

    private final StoreSettingsService storeSettingsService;
    private final WhatsappOrderService whatsappOrderService;

    public WhatsappWebhookController(
            StoreSettingsService storeSettingsService,
            WhatsappOrderService whatsappOrderService
    ) {
        this.storeSettingsService = storeSettingsService;
        this.whatsappOrderService = whatsappOrderService;
    }

    @GetMapping("/webhooks/whatsapp")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge
    ) {
        if ("subscribe".equals(mode)
                && verifyToken != null
                && verifyToken.equals(storeSettingsService.getWhatsappVerifyToken())) {
            return ResponseEntity.ok(challenge == null ? "" : challenge);
        }

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verificacion invalida");
    }

    @PostMapping("/webhooks/whatsapp")
    public ResponseEntity<Void> receiveMessage(@RequestBody String payload) {
        Thread.ofVirtual().start(() -> processPayload(payload));
        return ResponseEntity.ok().build();
    }

    private void processPayload(String payload) {
        try {
            JsonObject root = JsonParser.parseString(payload).getAsJsonObject();
            for (JsonElement entryElement : root.getAsJsonArray("entry")) {
                JsonObject entry = entryElement.getAsJsonObject();
                for (JsonElement changeElement : entry.getAsJsonArray("changes")) {
                    JsonObject value = changeElement.getAsJsonObject().getAsJsonObject("value");
                    String profileName = profileName(value);

                    if (!value.has("messages")) {
                        continue;
                    }

                    for (JsonElement messageElement : value.getAsJsonArray("messages")) {
                        JsonObject message = messageElement.getAsJsonObject();
                        if (!"text".equals(jsonString(message, "type"))) {
                            continue;
                        }

                        whatsappOrderService.handleIncomingText(
                                jsonString(message, "from"),
                                jsonString(message, "id"),
                                profileName,
                                jsonString(message.getAsJsonObject("text"), "body")
                        );
                    }
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo leer el webhook de WhatsApp.", e);
        }
    }

    private String profileName(JsonObject value) {
        try {
            if (!value.has("contacts")) {
                return "";
            }
            JsonObject contact = value.getAsJsonArray("contacts").get(0).getAsJsonObject();
            return jsonString(contact.getAsJsonObject("profile"), "name");
        } catch (RuntimeException e) {
            return "";
        }
    }

    private String jsonString(JsonObject object, String name) {
        if (object == null || !object.has(name) || object.get(name).isJsonNull()) {
            return "";
        }
        return object.get(name).getAsString();
    }
}
