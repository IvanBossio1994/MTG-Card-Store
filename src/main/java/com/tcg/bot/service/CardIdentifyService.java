package com.tcg.bot.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tcg.bot.model.InventoryCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ayuda a completar edición y número de las cartas que en el Sheet solo tienen el nombre.
 * <p>
 * Una carta puede aparecer con más de un nombre: el de las reglas (Dictate of Erebos), el alternativo de una
 * edición especial (Elli's Rage, Godzilla, Secret Lair...), el impreso en otro idioma, o solo una cara de una
 * carta doble. Por eso la app nunca completa sola: busca en Scryfall, muestra las ediciones posibles y la
 * persona elige.
 */
@Service
public class CardIdentifyService {

    static final int MAX_CANDIDATES = 60;

    private static final Logger log = LoggerFactory.getLogger(CardIdentifyService.class);
    private static final String API = "https://api.scryfall.com";

    /** Se separa para probar sin internet: recibe la URL y devuelve el JSON, o null si Scryfall no encontró nada. */
    public interface Fetcher {
        String get(String url) throws Exception;
    }

    public record Candidate(
            String name,
            String alternateName,
            String setName,
            String setCode,
            String collectorNumber,
            String releasedAt,
            List<String> finishes,
            String imageUrl,
            boolean matchedByAlternateName
    ) {
        public String finishesLabel() {
            List<String> labels = new ArrayList<>();
            for (String finish : finishes) {
                labels.add(switch (finish) {
                    case "nonfoil" -> "Normal";
                    case "foil" -> "Foil";
                    case "etched" -> "Etched";
                    default -> finish;
                });
            }
            return String.join(" · ", labels);
        }

        /** Si la carta salió en una sola terminación, se puede completar también esa columna. */
        public String onlyFinish() {
            return finishes.size() == 1 ? finishes.get(0) : "";
        }
    }

    private final Fetcher fetcher;

    @Autowired
    public CardIdentifyService() {
        this(httpFetcher());
    }

    CardIdentifyService(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    /** Cartas con stock o sin stock a las que les falta edición o número. */
    public static boolean needsIdentification(InventoryCard card) {
        if (card == null || blank(card.getName())) {
            return false;
        }
        boolean noSet = blank(card.getSetCode()) && blank(card.getSetName());
        return noSet || blank(card.getCollectorNumber());
    }

    /**
     * Ediciones posibles para lo que esté escrito en el Sheet. Busca por nombre (incluye nombres alternativos)
     * y, si no aparece nada, prueba con la búsqueda aproximada para corregir errores de tipeo.
     */
    public List<Candidate> candidates(String writtenName) throws Exception {
        String name = writtenName == null ? "" : writtenName.trim();
        if (name.isEmpty()) {
            return List.of();
        }

        List<Candidate> found = search("\"" + name.replace("\"", "") + "\"", name);
        if (!found.isEmpty()) {
            return found;
        }

        // Puede estar escrito en otro idioma (nombre impreso de una edición en español, japonés...).
        found = search("\"" + name.replace("\"", "") + "\" lang:any", name);
        if (!found.isEmpty()) {
            return found;
        }

        // Nada con ese texto: Scryfall corrige errores de tipeo con la búsqueda aproximada.
        String fuzzy = fetcher.get(API + "/cards/named?fuzzy=" + encode(name));
        if (fuzzy == null) {
            return List.of();
        }
        String realName = text(JsonParser.parseString(fuzzy).getAsJsonObject(), "name");
        if (realName.isEmpty()) {
            return List.of();
        }
        return search("!\"" + realName.replace("\"", "") + "\"", realName);
    }

    private List<Candidate> search(String query, String writtenName) throws Exception {
        String url = API + "/cards/search?unique=prints&order=released&dir=desc&include_extras=true&q=" + encode(query);
        String target = normalize(writtenName);
        Map<String, Candidate> result = new LinkedHashMap<>();

        int pages = 0;
        while (url != null && pages < 3 && result.size() < MAX_CANDIDATES) {
            String body = fetcher.get(url);
            if (body == null) {
                break;
            }
            JsonObject page = JsonParser.parseString(body).getAsJsonObject();
            JsonArray data = page.has("data") ? page.getAsJsonArray("data") : new JsonArray();
            for (JsonElement element : data) {
                Candidate candidate = candidate(element.getAsJsonObject(), target);
                if (candidate != null && result.size() < MAX_CANDIDATES) {
                    result.putIfAbsent(candidate.setCode() + "/" + candidate.collectorNumber(), candidate);
                }
            }
            url = page.has("has_more") && page.get("has_more").getAsBoolean() ? text(page, "next_page") : null;
            pages++;
        }

        // Primero las que coinciden con lo escrito; si escribieron el nombre alternativo, esas van arriba.
        List<Candidate> sorted = new ArrayList<>(result.values());
        sorted.sort((first, second) -> Boolean.compare(second.matchedByAlternateName(), first.matchedByAlternateName()));
        return sorted;
    }

    private static Candidate candidate(JsonObject card, String target) {
        String name = text(card, "name");
        String setCode = text(card, "set").toUpperCase(Locale.ROOT);
        String number = text(card, "collector_number");
        if (name.isEmpty() || setCode.isEmpty() || number.isEmpty()) {
            return null;
        }

        List<String> alternates = new ArrayList<>();
        addIfPresent(alternates, text(card, "flavor_name"));
        addIfPresent(alternates, text(card, "printed_name"));
        List<String> realNames = new ArrayList<>(List.of(name));
        if (card.has("card_faces")) {
            for (JsonElement face : card.getAsJsonArray("card_faces")) {
                JsonObject faceObject = face.getAsJsonObject();
                realNames.add(text(faceObject, "name"));
                addIfPresent(alternates, text(faceObject, "flavor_name"));
                addIfPresent(alternates, text(faceObject, "printed_name"));
            }
        }

        boolean byRealName = realNames.stream().anyMatch(real -> contains(normalize(real), target));
        String alternate = alternates.stream().filter(alt -> contains(normalize(alt), target)).findFirst()
                .orElse(alternates.isEmpty() ? "" : alternates.get(0));
        boolean byAlternate = !byRealName && alternates.stream().anyMatch(alt -> contains(normalize(alt), target));

        List<String> finishes = new ArrayList<>();
        if (card.has("finishes")) {
            card.getAsJsonArray("finishes").forEach(finish -> finishes.add(finish.getAsString()));
        }

        return new Candidate(name, alternate, text(card, "set_name"), setCode, number,
                text(card, "released_at"), finishes, image(card), byAlternate);
    }

    private static String image(JsonObject card) {
        JsonObject uris = card.has("image_uris") ? card.getAsJsonObject("image_uris") : null;
        if (uris == null && card.has("card_faces")) {
            JsonObject face = card.getAsJsonArray("card_faces").get(0).getAsJsonObject();
            uris = face.has("image_uris") ? face.getAsJsonObject("image_uris") : null;
        }
        return uris == null ? "" : text(uris, "small");
    }

    private static boolean contains(String value, String target) {
        return !target.isEmpty() && (value.equals(target) || value.contains(target));
    }

    /** Sin tildes, mayúsculas ni signos: "Elli's Rage" y "ellis rage" son lo mismo. */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String plain = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/ ]", "").replaceAll("\\s+", " ").trim();
    }

    private static void addIfPresent(List<String> list, String value) {
        if (!value.isEmpty() && !list.contains(value)) {
            list.add(value);
        }
    }

    private static String text(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Fetcher httpFetcher() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return url -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("User-Agent", "MTGCardStore/2.0")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() >= 400) {
                log.debug("Scryfall respondió {} para {}", response.statusCode(), url);
                throw new IllegalStateException("Scryfall respondió " + response.statusCode());
            }
            // Scryfall pide no más de 10 pedidos por segundo.
            Thread.sleep(100);
            return response.body();
        };
    }
}
