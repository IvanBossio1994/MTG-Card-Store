package com.tcg.bot.google;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.tcg.bot.compartido.AccesoSheet;
import com.tcg.bot.compartido.SesionGoogle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Login con el Gmail del comercio (OAuth). Lo usan la v1 (pantallas y Sheets) y la v2 ({@link SesionGoogle}).
 * El token se guarda en {@code <app.storage-dir>/google-oauth-token}, la misma carpeta de siempre.
 */
@Service
public class GoogleOAuthService implements SesionGoogle {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthService.class);
    private static final String APPLICATION_NAME = "TCG Inventory Bot";
    private static final String USER_ID = "default";
    private static final String AUTH_URI = "https://accounts.google.com/o/oauth2/auth";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URI = "https://openidconnect.googleapis.com/v1/userinfo";
    /** {@code email}: para mostrar "Conectado como {gmail}" en Configuración. */
    private static final List<String> SCOPES = List.of(SheetsScopes.SPREADSHEETS, "email");
    private static final long SEGUNDOS_MINIMOS_DE_TOKEN = 60;

    private final Path tokenDirectory;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile String gmailEnMemoria;

    /**
     * Crea el servicio con la carpeta de datos y el cliente OAuth de la build.
     * Ejemplo: {@code new GoogleOAuthService("./data", "id", "secreto")} → token en ./data/google-oauth-token.
     */
    public GoogleOAuthService(
            @Value("${app.storage-dir:./data}") String storageDirectory,
            @Value("${google.oauth.client-id:}") String clientId,
            @Value("${google.oauth.client-secret:}") String clientSecret
    ) {
        this.tokenDirectory = Paths.get(storageDirectory).resolve("google-oauth-token");
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public boolean hasOAuthClientConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    public boolean hasOAuthToken() {
        try {
            return loadCredential() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isReady() {
        return hasOAuthClientConfigured() && hasOAuthToken();
    }

    public String authorizationUrl(String redirectUri) throws Exception {
        return flow().newAuthorizationUrl()
                .setRedirectUri(redirectUri)
                .setAccessType("offline")
                .set("prompt", "consent")
                .build();
    }

    public void exchangeCode(String code, String redirectUri) throws Exception {
        var tokenResponse = flow().newTokenRequest(code)
                .setRedirectUri(redirectUri)
                .execute();
        flow().createAndStoreCredential(tokenResponse, USER_ID);
        gmailEnMemoria = null;
    }

    public Sheets getSheetsService() throws Exception {
        Credential credential = loadCredential();
        if (credential == null) {
            throw new IllegalStateException("Conecta Google desde Configuracion antes de sincronizar.");
        }

        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential
        )
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public void disconnect() throws Exception {
        gmailEnMemoria = null;
        deleteRecursively(tokenDirectory);
    }

    /**
     * Verifica que la sesión del Gmail siga vigente (renueva el token si está por vencer) y devuelve el Gmail.
     * Ejemplo: token vigente → {@code Optional.of("tienda@gmail.com")}; Google responde {@code invalid_grant} → 401
     * "Tu sesión de Google venció."; sesiones sin permiso {@code email} → {@code Optional.empty()}.
     */
    @Override
    public Optional<String> gmailConectado() {
        if (!hasOAuthClientConfigured()) {
            throw SesionGoogle.sesionVencida();
        }
        try {
            Credential credencial = loadCredential();
            if (credencial == null) {
                throw SesionGoogle.sesionVencida();
            }
            Long segundosRestantes = credencial.getExpiresInSeconds();
            if ((segundosRestantes == null || segundosRestantes < SEGUNDOS_MINIMOS_DE_TOKEN) && !credencial.refreshToken()) {
                throw SesionGoogle.sesionVencida();
            }
            return gmailDe(credencial);
        } catch (TokenResponseException e) {
            String error = e.getDetails() == null ? "" : e.getDetails().getError();
            log.info("Google rechazó la sesión del Gmail ({}).", error);
            throw SesionGoogle.sesionVencida();
        } catch (AccessDeniedException e) {
            log.warn("Windows no deja leer la carpeta de la sesión de Google ({}).", e.getFile());
            throw SesionGoogle.sinPermisoEnCarpeta();
        } catch (IOException e) {
            log.warn("No se pudo verificar la sesión de Google.", e);
            throw AccesoSheet.sinConexion();
        } catch (GeneralSecurityException e) {
            log.error("No se pudo crear la conexión segura con Google.", e);
            throw AccesoSheet.sinConexion();
        }
    }

    /** Pide el mail a Google una vez y lo guarda en memoria hasta que se cierre o cambie la sesión. */
    private Optional<String> gmailDe(Credential credencial) {
        if (gmailEnMemoria != null) {
            return Optional.of(gmailEnMemoria);
        }
        var pedido = HttpRequest.newBuilder(URI.create(USERINFO_URI))
                .header("Authorization", "Bearer " + credencial.getAccessToken())
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            HttpResponse<String> respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                return Optional.empty();
            }
            JsonObject datos = JsonParser.parseString(respuesta.body()).getAsJsonObject();
            if (!datos.has("email")) {
                return Optional.empty();
            }
            gmailEnMemoria = datos.get("email").getAsString();
            return Optional.of(gmailEnMemoria);
        } catch (IOException | JsonParseException | IllegalStateException e) {
            log.info("Google no informó el Gmail conectado: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private Credential loadCredential() throws IOException, GeneralSecurityException {
        return flow().loadCredential(USER_ID);
    }

    private GoogleAuthorizationCodeFlow flow() throws IOException, GeneralSecurityException {
        GoogleClientSecrets clientSecrets = clientSecrets();
        return new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                clientSecrets,
                SCOPES
        )
                .setDataStoreFactory(new FileDataStoreFactory(tokenDirectory.toFile()))
                .setAccessType("offline")
                .build();
    }

    private GoogleClientSecrets clientSecrets() {
        if (!hasOAuthClientConfigured()) {
            throw new IllegalStateException("Falta configurar el OAuth client de Google en esta build.");
        }

        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details()
                .setClientId(clientId.trim())
                .setClientSecret(clientSecret.trim())
                .setAuthUri(AUTH_URI)
                .setTokenUri(TOKEN_URI);

        return new GoogleClientSecrets().setInstalled(details);
    }

    private void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }

        try (var paths = Files.walk(path)) {
            for (Path item : paths.sorted((first, second) -> second.compareTo(first)).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }
}
