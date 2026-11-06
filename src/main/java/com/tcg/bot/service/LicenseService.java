package com.tcg.bot.service;

import com.tcg.bot.i18n.I18n;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Plan gratis / plan Tienda.
 * <p>
 * Gratis: hasta {@link #FREE_CARD_LIMIT} cartas distintas en el inventario, con todo lo demas habilitado.
 * Al pasarse se puede seguir buscando, vendiendo y reservando, pero no sumar cartas nuevas.
 * <p>
 * Tienda: una clave de licencia de Lemon Squeezy, validada directo contra su API publica de licencias
 * (no hace falta servidor propio ni base de datos). El resultado se guarda en data/license.properties y
 * vale {@link #OFFLINE_GRACE} sin conexion, para que un corte de internet no frene la tienda.
 * <p>
 * Una licencia = una cuenta de Google: la clave solo habilita el plan si la cuenta de Google conectada en la app
 * tiene el mismo mail con el que se compro. Asi se puede usar en varias PCs con la misma cuenta, pero no
 * prestarla a otra tienda.
 */
@Service
public class LicenseService {

    public static final int FREE_CARD_LIMIT = 300;
    static final Duration OFFLINE_GRACE = Duration.ofDays(7);

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    private static final String VALIDATE_URL = "https://api.lemonsqueezy.com/v1/licenses/validate";

    /** Respuesta minima de una validacion. */
    public record Validation(boolean valid, String status, String expiresAt, String email, String productId, String error) {
    }

    /** Se separa para poder probar sin internet. */
    public interface Validator {
        Validation validate(String licenseKey) throws Exception;
    }

    public record Status(boolean pro, String plan, String email, String expiresAt, String message, String maskedKey) {
    }

    private final Path file;
    private final Validator validator;
    private final Clock clock;
    private final String expectedProductId;
    private final String purchaseUrl;
    private final Supplier<String> googleEmail;

    private String licenseKey = "";
    private Validation lastValidation;
    private Instant validatedAt;

    @Autowired
    public LicenseService(
            @Value("${app.storage-dir:./data}") String storageDirectory,
            @Value("${license.lemonsqueezy.product-id:}") String expectedProductId,
            @Value("${license.purchase-url:}") String purchaseUrl,
            GoogleAccountService googleAccountService
    ) {
        this(Paths.get(storageDirectory).resolve("license.properties"), lemonSqueezyValidator(), Clock.systemUTC(),
                expectedProductId, purchaseUrl, () -> googleAccountService.status().email());
    }

    LicenseService(Path file, Validator validator, Clock clock, String expectedProductId, String purchaseUrl,
                   Supplier<String> googleEmail) {
        this.googleEmail = googleEmail;
        this.file = file;
        this.validator = validator;
        this.clock = clock;
        this.expectedProductId = expectedProductId == null ? "" : expectedProductId.trim();
        this.purchaseUrl = purchaseUrl == null ? "" : purchaseUrl.trim();
        load();
    }

    /** Link de compra, con el mail de la cuenta de Google ya cargado para que la clave quede a su nombre. */
    public String getPurchaseUrl() {
        String email = currentGoogleEmail();
        if (purchaseUrl.isEmpty() || email.isEmpty()) {
            return purchaseUrl;
        }
        return purchaseUrl + (purchaseUrl.contains("?") ? "&" : "?")
                + "checkout%5Bemail%5D=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
    }

    public synchronized boolean isPro() {
        if (licenseKey.isBlank() || lastValidation == null || validatedAt == null) {
            return false;
        }

        boolean active = lastValidation.valid() && usable(lastValidation.status());
        boolean fresh = validatedAt.plus(OFFLINE_GRACE).isAfter(clock.instant());
        return active && fresh && accountMatches();
    }

    /** La clave es de quien la compro: el mail de compra tiene que ser el de la cuenta de Google conectada. */
    private boolean accountMatches() {
        String buyer = nullToEmpty(lastValidation.email()).trim();
        if (buyer.isEmpty()) {
            return true;
        }
        return buyer.equalsIgnoreCase(currentGoogleEmail());
    }

    private String currentGoogleEmail() {
        String email = googleEmail == null ? null : googleEmail.get();
        return email == null ? "" : email.trim();
    }

    /** Cuantas cartas distintas mas se pueden agregar. Integer.MAX_VALUE en plan Tienda. */
    public int remainingNewCards(int currentDistinctCards) {
        if (isPro()) {
            return Integer.MAX_VALUE;
        }
        return Math.max(FREE_CARD_LIMIT - currentDistinctCards, 0);
    }

    public String limitMessage() {
        return I18n.t("license.limit", FREE_CARD_LIMIT);
    }

    public synchronized Status status() {
        if (isPro()) {
            return new Status(true, "Tienda", lastValidation.email(), lastValidation.expiresAt(),
                    I18n.t("license.active"), mask(licenseKey));
        }

        String message = I18n.t("license.free", FREE_CARD_LIMIT);
        boolean paidKey = !licenseKey.isBlank() && lastValidation != null && lastValidation.valid()
                && usable(lastValidation.status());
        if (paidKey && !accountMatches()) {
            message = currentGoogleEmail().isEmpty()
                    ? I18n.t("license.needsGoogle", lastValidation.email())
                    : I18n.t("license.otherAccount", lastValidation.email());
        } else if (!licenseKey.isBlank() && lastValidation != null && lastValidation.error() != null && !lastValidation.error().isBlank()) {
            message = I18n.t("license.notActive", lastValidation.error());
        } else if (!licenseKey.isBlank() && lastValidation != null && !usable(lastValidation.status())) {
            message = I18n.t("license.status", translate(lastValidation.status()));
        } else if (!licenseKey.isBlank()) {
            message = I18n.t("license.offline", OFFLINE_GRACE.toDays());
        }

        return new Status(false, "Gratis", lastValidation == null ? "" : lastValidation.email(), "", message, mask(licenseKey));
    }

    /** Guarda y valida una clave nueva. Devuelve el estado resultante. */
    public synchronized Status activate(String key) throws IOException {
        String trimmed = key == null ? "" : key.trim();
        if (trimmed.isEmpty()) {
            licenseKey = "";
            lastValidation = null;
            validatedAt = null;
            save();
            return status();
        }

        licenseKey = trimmed;
        refresh();
        return status();
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 12 * 60 * 60_000)
    public synchronized void refresh() {
        if (licenseKey.isBlank()) {
            return;
        }

        try {
            Validation validation = validator.validate(licenseKey);
            if (validation.valid() && !expectedProductId.isEmpty() && !expectedProductId.equals(validation.productId())) {
                validation = new Validation(false, validation.status(), validation.expiresAt(), validation.email(),
                        validation.productId(), "la clave es de otro producto");
            }
            lastValidation = validation;
            validatedAt = clock.instant();
            save();
        } catch (Exception e) {
            // Sin internet: se mantiene la ultima validacion mientras dure la gracia.
            log.info("No se pudo validar la licencia ahora: {}", e.getMessage());
        }
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            licenseKey = properties.getProperty("key", "");
            String at = properties.getProperty("validatedAt", "");
            validatedAt = at.isBlank() ? null : Instant.parse(at);
            lastValidation = new Validation(
                    Boolean.parseBoolean(properties.getProperty("valid", "false")),
                    properties.getProperty("status", ""),
                    properties.getProperty("expiresAt", ""),
                    properties.getProperty("email", ""),
                    properties.getProperty("productId", ""),
                    properties.getProperty("error", "")
            );
        } catch (Exception e) {
            log.warn("No se pudo leer la licencia guardada.", e);
        }
    }

    private void save() throws IOException {
        Properties properties = new Properties();
        properties.setProperty("key", licenseKey);
        if (lastValidation != null && validatedAt != null) {
            properties.setProperty("valid", Boolean.toString(lastValidation.valid()));
            properties.setProperty("status", nullToEmpty(lastValidation.status()));
            properties.setProperty("expiresAt", nullToEmpty(lastValidation.expiresAt()));
            properties.setProperty("email", nullToEmpty(lastValidation.email()));
            properties.setProperty("productId", nullToEmpty(lastValidation.productId()));
            properties.setProperty("error", nullToEmpty(lastValidation.error()));
            properties.setProperty("validatedAt", validatedAt.toString());
        }

        Files.createDirectories(file.getParent());
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "Licencia de MTG Card Store");
        }
    }

    private static Validator lemonSqueezyValidator() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return key -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(VALIDATE_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "license_key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return parse(response.body());
        };
    }

    static Validation parse(String body) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        boolean valid = json.has("valid") && !json.get("valid").isJsonNull() && json.get("valid").getAsBoolean();
        String error = string(json, "error");
        JsonObject license = json.has("license_key") && json.get("license_key").isJsonObject()
                ? json.getAsJsonObject("license_key") : new JsonObject();
        JsonObject meta = json.has("meta") && json.get("meta").isJsonObject()
                ? json.getAsJsonObject("meta") : new JsonObject();
        return new Validation(valid, string(license, "status"), string(license, "expires_at"),
                string(meta, "customer_email"), string(meta, "product_id"), error);
    }

    private static String string(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : "";
    }

    /**
     * Lemon Squeezy deja las claves recien compradas en "inactive" hasta que se activan en un equipo:
     * siguen siendo validas y pagas. Solo "expired" y "disabled" cortan el plan.
     */
    static boolean usable(String status) {
        return "active".equalsIgnoreCase(status) || "inactive".equalsIgnoreCase(status);
    }

    private static String translate(String status) {
        return switch (status == null ? "" : status.toLowerCase()) {
            case "expired" -> I18n.t("license.state.expired");
            case "disabled" -> I18n.t("license.state.disabled");
            case "inactive" -> I18n.t("license.state.inactive");
            default -> I18n.t("license.state.unknown");
        };
    }

    private static String mask(String key) {
        if (key == null || key.length() < 8) {
            return key == null ? "" : key;
        }
        return "••••" + key.substring(key.length() - 4);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
