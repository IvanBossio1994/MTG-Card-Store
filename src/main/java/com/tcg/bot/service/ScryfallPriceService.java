package com.tcg.bot.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Precios de TCGplayer (USD) y Cardmarket (EUR) tomados de Scryfall, que los publica gratis por carta.
 * Se piden de a 75 cartas con /cards/collection y se guardan en memoria 12 horas.
 * Si Scryfall no responde, la app sigue con Card Kingdom.
 */
@Service
public class ScryfallPriceService {

    static final int BATCH_SIZE = 75;
    static final Duration CACHE_TTL = Duration.ofHours(12);

    private static final Logger log = LoggerFactory.getLogger(ScryfallPriceService.class);
    private static final String COLLECTION_URL = "https://api.scryfall.com/cards/collection";

    public record CardRef(String setCode, String collectorNumber) {
        public String key() {
            return ScryfallPriceService.key(setCode, collectorNumber);
        }
    }

    /** Identificadores de la carta en otros sitios: sirven para subir el stock a Cardmarket, TCGplayer y otros. */
    public record Ids(String scryfallId, String tcgplayerId, String cardmarketId) {
        public static final Ids NONE = new Ids("", "", "");
    }

    public record Prices(Double usd, Double usdFoil, Double eur, Double eurFoil, Ids ids) {
        public Prices(Double usd, Double usdFoil, Double eur, Double eurFoil) {
            this(usd, usdFoil, eur, eurFoil, Ids.NONE);
        }

        public Double tcgplayer(boolean foil) {
            return foil ? (usdFoil != null ? usdFoil : usd) : (usd != null ? usd : usdFoil);
        }

        public Double cardmarket(boolean foil) {
            return foil ? (eurFoil != null ? eurFoil : eur) : (eur != null ? eur : eurFoil);
        }
    }

    /** Se separa para poder probar sin internet: recibe el JSON del pedido y devuelve el JSON de la respuesta. */
    public interface Fetcher {
        String post(String jsonBody) throws Exception;
    }

    private record Cached(Prices prices, Instant at) {
    }

    private final Fetcher fetcher;
    private final Clock clock;
    private final long pauseMillis;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public ScryfallPriceService() {
        this(httpFetcher(), Clock.systemUTC(), 100);
    }

    ScryfallPriceService(Fetcher fetcher, Clock clock, long pauseMillis) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.pauseMillis = pauseMillis;
    }

    /** Devuelve precios por clave set|numero. Las cartas que Scryfall no reconoce no aparecen. */
    public Map<String, Prices> prices(Collection<CardRef> cards) {
        Map<String, Prices> result = new HashMap<>();
        Map<String, CardRef> missing = new LinkedHashMap<>();
        Instant now = clock.instant();

        for (CardRef card : cards) {
            if (card == null || blank(card.setCode()) || blank(card.collectorNumber())) {
                continue;
            }
            Cached cached = cache.get(card.key());
            if (cached != null && cached.at().plus(CACHE_TTL).isAfter(now)) {
                if (cached.prices() != null) {
                    result.put(card.key(), cached.prices());
                }
            } else {
                missing.put(card.key(), card);
            }
        }

        List<CardRef> pending = new ArrayList<>(missing.values());
        for (int start = 0; start < pending.size(); start += BATCH_SIZE) {
            List<CardRef> batch = pending.subList(start, Math.min(start + BATCH_SIZE, pending.size()));
            try {
                Map<String, Prices> fetched = parse(fetcher.post(requestBody(batch)));
                for (CardRef card : batch) {
                    Prices prices = fetched.get(card.key());
                    cache.put(card.key(), new Cached(prices, now));
                    if (prices != null) {
                        result.put(card.key(), prices);
                    }
                }
                if (pauseMillis > 0 && start + BATCH_SIZE < pending.size()) {
                    Thread.sleep(pauseMillis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.info("Scryfall no respondió; se sigue solo con Card Kingdom. {}", e.getMessage());
                break;
            }
        }

        return result;
    }

    static String requestBody(List<CardRef> batch) {
        JsonArray identifiers = new JsonArray();
        for (CardRef card : batch) {
            JsonObject identifier = new JsonObject();
            identifier.addProperty("set", card.setCode().trim().toLowerCase(Locale.ROOT));
            identifier.addProperty("collector_number", normalizeNumber(card.collectorNumber()));
            identifiers.add(identifier);
        }
        JsonObject body = new JsonObject();
        body.add("identifiers", identifiers);
        return body.toString();
    }

    static Map<String, Prices> parse(String json) {
        Map<String, Prices> result = new HashMap<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("data") || !root.get("data").isJsonArray()) {
            return result;
        }

        for (JsonElement element : root.getAsJsonArray("data")) {
            JsonObject card = element.getAsJsonObject();
            JsonObject prices = card.has("prices") && card.get("prices").isJsonObject()
                    ? card.getAsJsonObject("prices") : new JsonObject();
            result.put(key(string(card, "set"), string(card, "collector_number")), new Prices(
                    number(prices, "usd"),
                    number(prices, "usd_foil"),
                    number(prices, "eur"),
                    number(prices, "eur_foil"),
                    new Ids(string(card, "id"), string(card, "tcgplayer_id"), string(card, "cardmarket_id"))
            ));
        }

        return result;
    }

    public static String key(String setCode, String collectorNumber) {
        return (setCode == null ? "" : setCode.trim().toLowerCase(Locale.ROOT)) + "|" + normalizeNumber(collectorNumber);
    }

    /** Card Kingdom usa "0410"; Scryfall, "410". Se conservan sufijos como "123a" o "★". */
    static String normalizeNumber(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        String withoutZeros = trimmed.replaceFirst("^0+(?=.)", "");
        return withoutZeros.isEmpty() ? trimmed : withoutZeros;
    }

    private static Double number(JsonObject object, String field) {
        if (!object.has(field) || object.get(field).isJsonNull()) {
            return null;
        }
        try {
            return Double.parseDouble(object.get(field).getAsString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String string(JsonObject object, String field) {
        return object.has(field) && !object.get(field).isJsonNull() ? object.get(field).getAsString() : "";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Fetcher httpFetcher() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return body -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(COLLECTION_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "MTGCardStore/2.0")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Scryfall respondió " + response.statusCode());
            }
            return response.body();
        };
    }
}
