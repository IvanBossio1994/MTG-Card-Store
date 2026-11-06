package com.tcg.bot.service;

import com.tcg.bot.i18n.I18n;
import com.google.auth.oauth2.UserCredentials;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Login con Google para usar el Sheet propio de cada persona, sin cuenta de servicio.
 * <p>
 * Usa un cliente OAuth "App de escritorio" con redireccion a la propia app local (loopback) y PKCE.
 * Pide solo {@code openid}, {@code email} y {@code drive.file}: la app ve unicamente los Sheets que ella misma
 * crea, no el resto del Drive. Las claves del cliente se leen de {@code data/google-oauth.properties}
 * (o de las variables TCG_GOOGLE_CLIENT_ID / TCG_GOOGLE_CLIENT_SECRET), nunca del repo.
 * El token de la cuenta conectada queda en {@code data/google-account.properties}, en la PC de la persona.
 */
@Service
public class GoogleAccountService {

    public static final String SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file";
    public static final List<String> SCOPES = List.of("openid", "email", SCOPE_DRIVE_FILE);
    public static final String CALLBACK_PATH = "/oauth2/callback";

    static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    static final String REVOKE_URL = "https://oauth2.googleapis.com/revoke";

    private static final Logger log = LoggerFactory.getLogger(GoogleAccountService.class);
    private static final Duration LOGIN_TIMEOUT = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Client(String id, String secret) {
    }

    public record Status(boolean configured, boolean connected, String email) {
    }

    /** Hace el POST de formulario contra Google. Se reemplaza en los tests. */
    public interface FormPoster {
        String post(String url, Map<String, String> form) throws Exception;
    }

    private record PendingLogin(String verifier, String redirectUri, Instant createdAt) {
    }

    public static class GoogleLoginException extends Exception {
        public GoogleLoginException(String message) {
            super(message);
        }
    }

    private final Path clientFile;
    private final Path accountFile;
    private final FormPoster poster;
    private final Clock clock;
    /** Cliente OAuth que trae el build de Windows (lo escribe el workflow desde los secrets de GitHub). */
    private final Properties bundled;
    private final Map<String, PendingLogin> pending = new ConcurrentHashMap<>();

    @Autowired
    public GoogleAccountService(@Value("${app.storage-dir:./data}") String storageDirectory) {
        this(Path.of(storageDirectory), GoogleAccountService::httpPost, Clock.systemUTC(), bundledClient());
    }

    GoogleAccountService(Path storageDirectory, FormPoster poster, Clock clock) {
        this(storageDirectory, poster, clock, new Properties());
    }

    GoogleAccountService(Path storageDirectory, FormPoster poster, Clock clock, Properties bundled) {
        this.bundled = bundled;
        this.clientFile = storageDirectory.resolve("google-oauth.properties");
        this.accountFile = storageDirectory.resolve("google-account.properties");
        this.poster = poster;
        this.clock = clock;
    }

    public Client client() {
        String envId = System.getenv("TCG_GOOGLE_CLIENT_ID");
        String envSecret = System.getenv("TCG_GOOGLE_CLIENT_SECRET");
        if (notBlank(envId) && notBlank(envSecret)) {
            return new Client(envId.trim(), envSecret.trim());
        }

        Client local = fromProperties(load(clientFile));
        return local != null ? local : fromProperties(bundled);
    }

    private static Client fromProperties(Properties properties) {
        String id = properties.getProperty("google.oauth.client-id", "").trim();
        String secret = properties.getProperty("google.oauth.client-secret", "").trim();
        return notBlank(id) && notBlank(secret) ? new Client(id, secret) : null;
    }

    private static Properties bundledClient() {
        Properties properties = new Properties();
        try (InputStream input = GoogleAccountService.class.getResourceAsStream("/google-oauth.properties")) {
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            log.warn("No se pudo leer el cliente OAuth incluido en la app.", e);
        }
        return properties;
    }

    public boolean isConfigured() {
        return client() != null;
    }

    public synchronized boolean isConnected() {
        return isConfigured() && notBlank(load(accountFile).getProperty("refresh-token"));
    }

    public synchronized Status status() {
        Properties account = load(accountFile);
        boolean connected = isConfigured() && notBlank(account.getProperty("refresh-token"));
        return new Status(isConfigured(), connected, connected ? account.getProperty("email", "") : "");
    }

    /** Arma el link a la pantalla de permisos de Google. */
    public String authorizationUrl(String redirectUri) throws GoogleLoginException {
        Client client = client();
        if (client == null) {
            throw new GoogleLoginException(I18n.t("google.err.noConfig"));
        }

        dropOldLogins();
        String state = randomToken(24);
        String verifier = randomToken(48);
        pending.put(state, new PendingLogin(verifier, redirectUri, clock.instant()));

        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", client.id());
        params.put("redirect_uri", redirectUri);
        params.put("response_type", "code");
        params.put("scope", String.join(" ", SCOPES));
        params.put("state", state);
        params.put("code_challenge", challenge(verifier));
        params.put("code_challenge_method", "S256");
        params.put("access_type", "offline");
        params.put("prompt", "consent");
        return AUTH_URL + "?" + form(params);
    }

    /** Google vuelve a la app con el codigo: se cambia por el token y se guarda la cuenta. */
    public synchronized Status completeLogin(String state, String code, String error) throws Exception {
        if (notBlank(error)) {
            throw new GoogleLoginException("access_denied".equals(error)
                    ? I18n.t("google.err.denied")
                    : I18n.t("google.err.noPermission", error));
        }

        PendingLogin login = state == null ? null : pending.remove(state);
        if (login == null || login.createdAt().plus(LOGIN_TIMEOUT).isBefore(clock.instant())) {
            throw new GoogleLoginException(I18n.t("google.err.expired"));
        }

        if (!notBlank(code)) {
            throw new GoogleLoginException(I18n.t("google.err.noCode"));
        }

        Client client = client();
        if (client == null) {
            throw new GoogleLoginException(I18n.t("google.err.noConfig"));
        }

        Map<String, String> request = new LinkedHashMap<>();
        request.put("code", code);
        request.put("client_id", client.id());
        request.put("client_secret", client.secret());
        request.put("redirect_uri", login.redirectUri());
        request.put("grant_type", "authorization_code");
        request.put("code_verifier", login.verifier());

        JsonObject token = JsonParser.parseString(poster.post(TOKEN_URL, request)).getAsJsonObject();
        if (token.has("error")) {
            throw new GoogleLoginException(I18n.t("google.err.rejected", text(token, "error")));
        }

        String scope = text(token, "scope");
        if (!scope.contains(SCOPE_DRIVE_FILE)) {
            throw new GoogleLoginException(I18n.t("google.err.driveScope"));
        }

        String refreshToken = text(token, "refresh_token");
        if (!notBlank(refreshToken)) {
            throw new GoogleLoginException(I18n.t("google.err.noRefresh"));
        }

        Properties account = new Properties();
        account.setProperty("refresh-token", refreshToken);
        account.setProperty("email", emailFromIdToken(text(token, "id_token")));
        account.setProperty("connected-at", clock.instant().toString());
        save(account);
        return status();
    }

    /** Credenciales de la cuenta conectada, o null si no hay. Se renuevan solas. */
    public synchronized UserCredentials credentials() {
        Client client = client();
        String refreshToken = load(accountFile).getProperty("refresh-token");
        if (client == null || !notBlank(refreshToken)) {
            return null;
        }

        return UserCredentials.newBuilder()
                .setClientId(client.id())
                .setClientSecret(client.secret())
                .setRefreshToken(refreshToken)
                .build();
    }

    public synchronized void disconnect() throws IOException {
        String refreshToken = load(accountFile).getProperty("refresh-token");
        if (notBlank(refreshToken)) {
            try {
                poster.post(REVOKE_URL, Map.of("token", refreshToken));
            } catch (Exception e) {
                log.debug("No se pudo revocar el token en Google: {}", e.getMessage());
            }
        }
        Files.deleteIfExists(accountFile);
    }

    static String emailFromIdToken(String idToken) {
        // El id_token llega directo de Google por HTTPS en la respuesta del token; solo se lee el mail.
        if (!notBlank(idToken)) {
            return "";
        }
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            return "";
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return text(JsonParser.parseString(payload).getAsJsonObject(), "email");
        } catch (RuntimeException e) {
            return "";
        }
    }

    static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void dropOldLogins() {
        Instant limit = clock.instant().minus(LOGIN_TIMEOUT);
        pending.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(limit));
    }

    private void save(Properties account) throws IOException {
        Files.createDirectories(accountFile.getParent());
        try (OutputStream output = Files.newOutputStream(accountFile)) {
            account.store(output, "Cuenta de Google conectada. No compartir este archivo.");
        }
    }

    private static Properties load(Path file) {
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            } catch (IOException e) {
                log.warn("No se pudo leer {}: {}", file, e.getMessage());
            }
        }
        return properties;
    }

    private static String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private static String form(Map<String, String> params) {
        return params.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String text(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String httpPost(String url, Map<String, String> params) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(params)))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
