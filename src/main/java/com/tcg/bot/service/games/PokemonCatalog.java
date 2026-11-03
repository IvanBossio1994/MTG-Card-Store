package com.tcg.bot.service.games;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tcg.bot.model.Game;
import com.tcg.bot.model.GamePrint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cartas de Pokémon. Primero TCGdex (gratis, sin clave ni límite publicado); si falla, pokemontcg.io,
 * que sin clave permite 30 consultas por minuto y está pasando a ser pago (Scrydex).
 * Cada terminación con precio en TCGplayer (normal, holo, reverse holo, 1st edition) es una impresión distinta.
 * Con la variable TCG_POKEMON_API_KEY, pokemontcg.io sube a 20.000 consultas por día.
 */
@Service
public class PokemonCatalog implements GameCatalog {

    private static final String API = "https://api.pokemontcg.io/v2/cards";
    private static final Map<String, String> FINISHES = Map.of(
            "normal", "Normal",
            "holofoil", "Holo",
            "reverseHolofoil", "Reverse Holo",
            "1stEditionHolofoil", "1st Edition Holo",
            "1stEditionNormal", "1st Edition",
            "unlimitedHolofoil", "Unlimited Holo",
            "unlimited", "Unlimited");

    private static final Logger log = LoggerFactory.getLogger(PokemonCatalog.class);

    private final TcgdexSource tcgdex;
    private final Fetcher fetcher;

    @Autowired
    public PokemonCatalog() {
        this(GameCatalog.httpFetcher(null, null),
                GameCatalog.httpFetcher("X-Api-Key", System.getenv("TCG_POKEMON_API_KEY")));
    }

    /** Solo pokemontcg.io (para pruebas). */
    PokemonCatalog(Fetcher fetcher) {
        this(null, fetcher);
    }

    PokemonCatalog(Fetcher tcgdexFetcher, Fetcher pokemonTcgFetcher) {
        this.tcgdex = tcgdexFetcher == null ? null : new TcgdexSource(tcgdexFetcher);
        this.fetcher = pokemonTcgFetcher;
    }

    @Override
    public Game game() {
        return Game.POKEMON;
    }

    @Override
    public List<GamePrint> search(String name) throws Exception {
        String clean = name == null ? "" : name.trim().replace("\"", "");
        if (clean.isEmpty()) {
            return List.of();
        }
        if (tcgdex != null) {
            try {
                List<GamePrint> found = tcgdex.search(clean);
                if (!found.isEmpty()) {
                    return found;
                }
            } catch (Exception e) {
                log.info("TCGdex no respondió ({}); pruebo con pokemontcg.io.", e.getMessage());
            }
        }
        return searchPokemonTcg(clean);
    }

    private List<GamePrint> searchPokemonTcg(String clean) throws Exception {
        String query = clean.contains(" ") ? "name:\"" + clean + "\"" : "name:" + clean + "*";
        String body = fetcher.get(API + "?pageSize=60&orderBy=-set.releaseDate&q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
        if (body == null) {
            return List.of();
        }

        List<GamePrint> prints = new ArrayList<>();
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("data")) {
            return prints;
        }
        for (JsonElement element : root.getAsJsonArray("data")) {
            JsonObject card = element.getAsJsonObject();
            JsonObject set = card.has("set") ? card.getAsJsonObject("set") : new JsonObject();
            String image = card.has("images") ? text(card.getAsJsonObject("images"), "small") : "";
            String setCode = text(set, "ptcgoCode").isEmpty() ? text(set, "id").toUpperCase() : text(set, "ptcgoCode");
            JsonObject prices = card.has("tcgplayer") && card.getAsJsonObject("tcgplayer").has("prices")
                    ? card.getAsJsonObject("tcgplayer").getAsJsonObject("prices") : null;

            if (prices == null || prices.isEmpty()) {
                prints.add(new GamePrint(text(card, "id"), text(card, "name"), text(set, "name"), setCode,
                        text(card, "number"), text(card, "rarity"), "", image, null, text(set, "releaseDate")));
                continue;
            }
            for (var entry : prices.entrySet()) {
                JsonObject price = entry.getValue().getAsJsonObject();
                Double usd = number(price, "market");
                if (usd == null) {
                    usd = number(price, "mid");
                }
                prints.add(new GamePrint(text(card, "id") + ":" + entry.getKey(), text(card, "name"), text(set, "name"),
                        setCode, text(card, "number"), text(card, "rarity"),
                        FINISHES.getOrDefault(entry.getKey(), entry.getKey()), image, usd, text(set, "releaseDate")));
            }
        }
        return prints;
    }

    static String text(JsonObject json, String key) {
        return json != null && json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }

    static Double number(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        try {
            return json.get(key).getAsDouble();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
