package com.tcg.bot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;

/**
 * Imágenes de cartas guardadas en la PC: cada una se baja una sola vez y después se sirve desde el disco.
 * Scryfall y YGOPRODeck piden no pedir la misma imagen una y otra vez (YGOPRODeck bloquea la IP si se abusa).
 * Entre descargas nuevas se espera un poco para no hacer ráfagas.
 */
@Service
public class ImageCacheService {

    private static final Logger log = LoggerFactory.getLogger(ImageCacheService.class);

    /** Solo se baja de estos sitios: la app no sirve de puente para cualquier URL. */
    static final Set<String> ALLOWED_HOSTS = Set.of(
            "api.scryfall.com", "cards.scryfall.io",
            "images.ygoprodeck.com",
            "assets.tcgdex.net",
            "images.pokemontcg.io");
    static final long MIN_GAP_MS = 120;
    static final long MAX_BYTES = 3_000_000;

    public record Image(byte[] bytes, String contentType) {
    }

    private final StoreSettingsService settings;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private long lastDownload;

    public ImageCacheService(StoreSettingsService settings) {
        this.settings = settings;
    }

    public static boolean allowed(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && ALLOWED_HOSTS.contains(uri.getHost().toLowerCase(java.util.Locale.ROOT));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** La imagen de esa URL, desde el disco si ya se bajó. null si no se pudo conseguir. */
    public Image get(String url) {
        if (url == null || !allowed(url)) {
            return null;
        }
        Path folder = Paths.get(settings.getCacheDirectory()).resolve("images");
        String key = sha256(url);
        Path file = folder.resolve(key + ".img");
        Path type = folder.resolve(key + ".type");
        try {
            if (Files.exists(file)) {
                String contentType = Files.exists(type) ? Files.readString(type).trim() : "image/jpeg";
                return new Image(Files.readAllBytes(file), contentType);
            }
            Image image = download(url);
            if (image == null) {
                return null;
            }
            Files.createDirectories(folder);
            Path temp = Files.createTempFile(folder, key, ".tmp");
            Files.write(temp, image.bytes());
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(type, image.contentType());
            return image;
        } catch (Exception e) {
            log.debug("No se pudo conseguir la imagen {}: {}", url, e.getMessage());
            return null;
        }
    }

    private Image download(String url) throws Exception {
        String current = url;
        for (int hop = 0; hop < 3; hop++) {
            if (!allowed(current)) {
                return null;
            }
            waitTurn();
            HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(URI.create(current))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "MTGCardStore/2.0")
                    .header("Accept", "image/*")
                    .GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null) {
                    return null;
                }
                current = URI.create(current).resolve(location).toString();
                continue;
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (status != 200 || !contentType.startsWith("image/") || response.body().length > MAX_BYTES) {
                return null;
            }
            return new Image(response.body(), contentType);
        }
        return null;
    }

    private synchronized void waitTurn() throws InterruptedException {
        long wait = lastDownload + MIN_GAP_MS - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
        lastDownload = System.currentTimeMillis();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
