package com.tcg.bot.service.games;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tcg.bot.model.Game;
import com.tcg.bot.model.GamePrint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Cartas de Yu-Gi-Oh! desde YGOPRODeck (gratis). Cada set y rareza en que salió una carta es una impresión
 * distinta, con su precio de referencia en dólares.
 */
@Service
public class YugiohCatalog implements GameCatalog {

    private static final String API = "https://db.ygoprodeck.com/api/v7/cardinfo.php";
    private static final int MAX_PRINTS = 80;

    private final Fetcher fetcher;

    @Autowired
    public YugiohCatalog() {
        this(GameCatalog.httpFetcher(null, null));
    }

    YugiohCatalog(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public Game game() {
        return Game.YUGIOH;
    }

    @Override
    public List<GamePrint> search(String name) throws Exception {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            return List.of();
        }
        String body = fetcher.get(API + "?num=25&offset=0&fname=" + URLEncoder.encode(clean, StandardCharsets.UTF_8));
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
            String cardName = PokemonCatalog.text(card, "name");
            String image = card.has("card_images") && !card.getAsJsonArray("card_images").isEmpty()
                    ? PokemonCatalog.text(card.getAsJsonArray("card_images").get(0).getAsJsonObject(), "image_url_small") : "";
            if (!card.has("card_sets")) {
                continue;
            }
            for (JsonElement setElement : card.getAsJsonArray("card_sets")) {
                if (prints.size() >= MAX_PRINTS) {
                    return prints;
                }
                JsonObject set = setElement.getAsJsonObject();
                String setCode = PokemonCatalog.text(set, "set_code");
                String rarity = PokemonCatalog.text(set, "set_rarity");
                String rarityCode = PokemonCatalog.text(set, "set_rarity_code").replaceAll("[()]", "");
                Double usd = null;
                try {
                    double value = Double.parseDouble(PokemonCatalog.text(set, "set_price"));
                    usd = value > 0 ? value : null;
                } catch (NumberFormatException ignored) {
                    // Sin precio para esa impresión.
                }
                String prefix = setCode.contains("-") ? setCode.substring(0, setCode.indexOf('-')) : setCode;
                String number = setCode.contains("-") ? setCode.substring(setCode.indexOf('-') + 1) : "";
                prints.add(new GamePrint(setCode + ":" + (rarityCode.isEmpty() ? rarity : rarityCode), cardName,
                        PokemonCatalog.text(set, "set_name"), prefix, number, rarity, "", image, usd, ""));
            }
        }
        return prints;
    }
}
