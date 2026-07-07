package com.tcg.bot.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class GoogleOAuthService {

    private static final String APPLICATION_NAME = "TCG Inventory Bot";
    private static final String USER_ID = "default";
    private static final String AUTH_URI = "https://accounts.google.com/o/oauth2/auth";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";

    private final StoreSettingsService storeSettingsService;
    private final String clientId;
    private final String clientSecret;

    public GoogleOAuthService(
            StoreSettingsService storeSettingsService,
            @Value("${google.oauth.client-id:}") String clientId,
            @Value("${google.oauth.client-secret:}") String clientSecret
    ) {
        this.storeSettingsService = storeSettingsService;
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
        deleteRecursively(storeSettingsService.getGoogleOAuthTokenDirectory());
    }

    private Credential loadCredential() throws Exception {
        return flow().loadCredential(USER_ID);
    }

    private GoogleAuthorizationCodeFlow flow() throws Exception {
        GoogleClientSecrets clientSecrets = clientSecrets();
        return new GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                clientSecrets,
                List.of(SheetsScopes.SPREADSHEETS)
        )
                .setDataStoreFactory(new FileDataStoreFactory(storeSettingsService.getGoogleOAuthTokenDirectory().toFile()))
                .setAccessType("offline")
                .build();
    }

    private GoogleClientSecrets clientSecrets() throws Exception {
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
