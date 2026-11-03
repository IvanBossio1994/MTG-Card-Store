package com.tcg.bot.service;

import com.tcg.bot.service.GoogleAccountService.GoogleLoginException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GoogleAccountServiceTest {

    @TempDir
    Path dir;

    final List<Map<String, String>> posts = new ArrayList<>();
    String tokenResponse;
    GoogleAccountService service;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(dir.resolve("google-oauth.properties"),
                "google.oauth.client-id=test-id.apps.googleusercontent.com\ngoogle.oauth.client-secret=test-secret\n");
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"email\":\"tienda@example.com\"}".getBytes(StandardCharsets.UTF_8));
        tokenResponse = "{\"access_token\":\"a\",\"refresh_token\":\"r-123\",\"id_token\":\"h." + payload + ".s\","
                + "\"scope\":\"openid https://www.googleapis.com/auth/userinfo.email " + GoogleAccountService.SCOPE_DRIVE_FILE + "\"}";
        service = new GoogleAccountService(dir, (url, form) -> {
            Map<String, String> copy = new HashMap<>(form);
            copy.put("_url", url);
            posts.add(copy);
            return url.equals(GoogleAccountService.TOKEN_URL) ? tokenResponse : "{}";
        }, Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void authorizationUrlAsksOnlyForMinimalScopesWithPkce() throws Exception {
        Map<String, String> params = query(service.authorizationUrl("http://127.0.0.1:8080/oauth2/callback"));

        assertEquals("openid email https://www.googleapis.com/auth/drive.file", params.get("scope"));
        assertEquals("S256", params.get("code_challenge_method"));
        assertEquals("offline", params.get("access_type"));
        assertEquals("test-id.apps.googleusercontent.com", params.get("client_id"));
        assertNotNull(params.get("state"));
    }

    @Test
    void completeLoginSavesAccountAndSendsVerifier() throws Exception {
        Map<String, String> params = query(service.authorizationUrl("http://127.0.0.1:8080/oauth2/callback"));

        var status = service.completeLogin(params.get("state"), "code-1", null);

        assertTrue(status.connected());
        assertEquals("tienda@example.com", status.email());
        Map<String, String> sent = posts.get(0);
        assertEquals("code-1", sent.get("code"));
        assertEquals(params.get("code_challenge"), GoogleAccountService.challenge(sent.get("code_verifier")));
        assertNotNull(service.credentials());
    }

    @Test
    void rejectsUnknownStateAndCannotReuseIt() throws Exception {
        Map<String, String> params = query(service.authorizationUrl("http://127.0.0.1:8080/oauth2/callback"));

        assertThrows(GoogleLoginException.class, () -> service.completeLogin("otro", "code", null));
        service.completeLogin(params.get("state"), "code", null);
        assertThrows(GoogleLoginException.class, () -> service.completeLogin(params.get("state"), "code", null));
    }

    @Test
    void rejectsLoginWithoutDrivePermission() throws Exception {
        tokenResponse = "{\"refresh_token\":\"r\",\"scope\":\"openid email\"}";
        Map<String, String> params = query(service.authorizationUrl("http://127.0.0.1:8080/oauth2/callback"));

        assertThrows(GoogleLoginException.class, () -> service.completeLogin(params.get("state"), "code", null));
        assertFalse(service.status().connected());
    }

    @Test
    void disconnectRevokesAndForgetsTheAccount() throws Exception {
        Map<String, String> params = query(service.authorizationUrl("http://127.0.0.1:8080/oauth2/callback"));
        service.completeLogin(params.get("state"), "code", null);

        service.disconnect();

        assertFalse(service.status().connected());
        assertEquals(GoogleAccountService.REVOKE_URL, posts.get(posts.size() - 1).get("_url"));
    }

    @Test
    void notConfiguredWithoutClientFile() throws Exception {
        Files.delete(dir.resolve("google-oauth.properties"));
        assertFalse(service.status().configured());
        assertThrows(GoogleLoginException.class, () -> service.authorizationUrl("http://127.0.0.1:8080/oauth2/callback"));
    }

    private static Map<String, String> query(String url) {
        Map<String, String> params = new HashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            params.put(parts[0], URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return params;
    }
}
