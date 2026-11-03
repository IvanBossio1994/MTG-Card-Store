package com.tcg.bot.service.games;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tcg.bot.model.GamePrint;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pokémon desde TCGdex (api.tcgdex.net): gratis, sin clave y con precios de TCGplayer.
 * La lista por nombre trae solo lo básico, así que se pide el detalle de las primeras cartas
 * (de a una, sin ráfagas). Las respuestas se guardan en {@link GameInventoryService} 1 h 5 min.
 */
class TcgdexSource {

    static final String API = "https://api.tcgdex.net/v2/en/cards";
    /** Cuántas cartas se detallan por búsqueda: alcanza para elegir y no castiga la API. */
    static final int MAX_DETAILS = 16;

    /** Mismos nombres de terminación que usaba pokemontcg.io, para que las filas viejas sigan coincidiendo. */
    private static final Map<String, String> FINISHES = Map.of(
            "normal", "Normal",
            "holo", "Holo",
            "holofoil", "Holo",
            "reverse", "Reverse Holo",
            "reverse-holofoil", "Reverse Holo",
            "reverseHolofoil", "Reverse Holo",
            "1stEditionHolofoil", "1st Edition Holo",
            "1stEditionNormal", "1st Edition");

    private final GameCatalog.Fetcher fetcher;

    TcgdexSource(GameCatalog.Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    List<GamePrint> search(String name) throws Exception {
        String body = fetcher.get(API + "?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8));
        if (body == null) {
            return List.of();
        }
        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonArray()) {
            return List.of();
        }
        List<JsonObject> briefs = new ArrayList<>();
        for (JsonElement element : parsed.getAsJsonArray()) {
            if (element.isJsonObject()) {
                briefs.add(element.getAsJsonObject());
            }
        }
        // Primero el nombre exacto ("Pikachu" antes que "Pikachu & Zekrom-GX").
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        briefs.sort(Comparator.comparing((JsonObject brief) -> !PokemonCatalog.text(brief, "name").toLowerCase(Locale.ROOT).equals(wanted)));

        List<GamePrint> prints = new ArrayList<>();
        for (JsonObject brief : briefs.subList(0, Math.min(MAX_DETAILS, briefs.size()))) {
            String id = PokemonCatalog.text(brief, "id");
            if (id.isEmpty()) {
                continue;
            }
            String detail = fetcher.get(API + "/" + URLEncoder.encode(id, StandardCharsets.UTF_8));
            if (detail != null) {
                prints.addAll(prints(JsonParser.parseString(detail).getAsJsonObject()));
            }
        }
        return prints;
    }

    static List<GamePrint> prints(JsonObject card) {
        JsonObject set = card.has("set") && card.get("set").isJsonObject() ? card.getAsJsonObject("set") : new JsonObject();
        String image = PokemonCatalog.text(card, "image");
        if (!image.isEmpty()) {
            image = image + "/low.webp";
        }
        String id = PokemonCatalog.text(card, "id");
        String setCode = PokemonCatalog.text(set, "id").toUpperCase(Locale.ROOT);
        String number = PokemonCatalog.text(card, "localId");
        String rarity = PokemonCatalog.text(card, "rarity");
        String setName = PokemonCatalog.text(set, "name");
        String cardName = PokemonCatalog.text(card, "name");

        JsonObject tcgplayer = null;
        if (card.has("pricing") && card.get("pricing").isJsonObject()) {
            JsonObject pricing = card.getAsJsonObject("pricing");
            if (pricing.has("tcgplayer") && pricing.get("tcgplayer").isJsonObject()) {
                tcgplayer = pricing.getAsJsonObject("tcgplayer");
            }
        }

        List<GamePrint> prints = new ArrayList<>();
        if (tcgplayer != null) {
            for (var entry : tcgplayer.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue; // "updated", "unit"
                }
                JsonObject price = entry.getValue().getAsJsonObject();
                Double usd = PokemonCatalog.number(price, "marketPrice");
                if (usd == null) {
                    usd = PokemonCatalog.number(price, "midPrice");
                }
                prints.add(new GamePrint(id + ":" + entry.getKey(), cardName, setName, setCode, number, rarity,
                        FINISHES.getOrDefault(entry.getKey(), entry.getKey()), image, usd, ""));
            }
        }
        if (prints.isEmpty()) {
            // Sin precio: se muestra igual, con las terminaciones que existen.
            List<String> finishes = new ArrayList<>();
            if (card.has("variants") && card.get("variants").isJsonObject()) {
                for (var entry : card.getAsJsonObject("variants").entrySet()) {
                    if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsBoolean() && FINISHES.containsKey(entry.getKey())) {
                        finishes.add(entry.getKey());
                    }
                }
            }
            if (finishes.isEmpty()) {
                prints.add(new GamePrint(id, cardName, setName, setCode, number, rarity, "", image, null, ""));
            }
            for (String finish : finishes) {
                prints.add(new GamePrint(id + ":" + finish, cardName, setName, setCode, number, rarity,
                        FINISHES.get(finish), image, null, ""));
            }
        }
        return prints;
    }

    static JsonArray empty() {
        return new JsonArray();
    }
}
