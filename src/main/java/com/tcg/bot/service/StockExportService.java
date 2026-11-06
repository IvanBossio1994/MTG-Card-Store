package com.tcg.bot.service;

import com.tcg.bot.model.Game;
import com.tcg.bot.model.GameCard;
import com.tcg.bot.model.InventoryCard;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exporta el stock a CSV para subirlo a otros sitios. Los nombres de columna siguen el formato de Moxfield
 * (Count, Name, Edition, Collector Number, Foil, Condition, Language), que entienden Moxfield, ManaBox,
 * Deckbox y TCG PowerTools (que a su vez publica en Cardmarket y CardTrader). Para Magic se suman los IDs
 * de Scryfall, TCGplayer y Cardmarket, que es como esos sitios reconocen la carta exacta.
 */
@Service
public class StockExportService {

    static final List<String> MAGIC_HEADER = List.of(
            "Count", "Name", "Edition", "Set Name", "Collector Number", "Foil", "Condition", "Language", "Price",
            "Scryfall ID", "TCGplayer ID", "Cardmarket ID", "Location");
    static final List<String> GAME_HEADER = List.of(
            "Count", "Name", "Edition", "Set Name", "Collector Number", "Finish", "Rarity", "Price", "Location");

    private final InventoryStore store;
    private final ScryfallPriceService scryfall;

    public StockExportService(InventoryStore store, ScryfallPriceService scryfall) {
        this.store = store;
        this.scryfall = scryfall;
    }

    public String magicCsv() throws Exception {
        List<InventoryCard> cards = store.getInventoryCards().stream()
                .filter(card -> card.getName() != null && !card.getName().isBlank())
                .filter(card -> StockOperationsService.quantity(card) > 0)
                .toList();
        Map<String, ScryfallPriceService.Prices> extra = scryfall.prices(cards.stream()
                .map(card -> new ScryfallPriceService.CardRef(card.getSetCode(), card.getCollectorNumber()))
                .toList());

        StringBuilder csv = new StringBuilder();
        line(csv, MAGIC_HEADER);
        for (InventoryCard card : cards) {
            var found = extra.get(ScryfallPriceService.key(card.getSetCode(), card.getCollectorNumber()));
            var ids = found == null || found.ids() == null ? ScryfallPriceService.Ids.NONE : found.ids();
            line(csv, List.of(
                    String.valueOf(StockOperationsService.quantity(card)),
                    safe(card.getName()),
                    safe(card.getSetCode()).toLowerCase(Locale.ROOT),
                    safe(card.getSetName()),
                    safe(card.getCollectorNumber()).replaceFirst("^0+(?=\\d)", ""),
                    card.isFoil() ? "foil" : "",
                    condition(card.getCondition()),
                    language(card.getLanguage()),
                    card.localPriceValue() > 0 ? com.tcg.bot.i18n.Money.plain(card.localPriceValue()) : "",
                    ids.scryfallId(),
                    ids.tcgplayerId(),
                    ids.cardmarketId(),
                    safe(card.getLocation())));
        }
        return csv.toString();
    }

    public String gameCsv(Game game) throws Exception {
        StringBuilder csv = new StringBuilder();
        line(csv, GAME_HEADER);
        for (GameCard card : store.getGameCards(game)) {
            if (card.getQuantity() <= 0 || card.getName() == null || card.getName().isBlank()) {
                continue;
            }
            line(csv, List.of(
                    String.valueOf(card.getQuantity()),
                    safe(card.getName()),
                    safe(card.getSetCode()),
                    safe(card.getSetName()),
                    safe(card.getNumber()),
                    safe(card.getFinish()),
                    safe(card.getRarity()),
                    card.getLocalPrice() > 0 ? com.tcg.bot.i18n.Money.plain(card.getLocalPrice()) : "",
                    safe(card.getLocation())));
        }
        return csv.toString();
    }

    /** Estados abreviados de la tienda a los nombres largos que usan Moxfield y los marketplaces. */
    static String condition(String value) {
        String key = safe(value).trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return switch (key) {
            case "", "nm", "nearmint", "m", "mint", "new", "nuevo", "nueva" -> "Near Mint";
            case "lp", "ex", "excellent", "lightlyplayed", "sp", "slightlyplayed" -> "Lightly Played";
            case "mp", "vg", "gd", "good", "moderatelyplayed", "played", "pl" -> "Moderately Played";
            case "hp", "heavilyplayed", "po", "poor" -> "Heavily Played";
            case "dmg", "d", "damaged", "danada", "dañada" -> "Damaged";
            default -> safe(value).trim();
        };
    }

    /** Idiomas de la tienda a los nombres en inglés que usan Moxfield y los marketplaces. */
    static String language(String value) {
        String key = java.text.Normalizer.normalize(safe(value).trim().toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return switch (key) {
            case "", "en", "eng", "english", "ingles" -> "English";
            case "es", "sp", "spa", "spanish", "espanol", "castellano" -> "Spanish";
            case "pt", "por", "portuguese", "portugues" -> "Portuguese";
            case "fr", "fra", "french", "frances" -> "French";
            case "de", "ger", "german", "aleman" -> "German";
            case "it", "ita", "italian", "italiano" -> "Italian";
            case "ja", "jp", "jpn", "japanese", "japones" -> "Japanese";
            case "ko", "kr", "kor", "korean", "coreano" -> "Korean";
            case "ru", "rus", "russian", "ruso" -> "Russian";
            case "zhs", "zh", "cs", "chinese", "chino", "simplified chinese" -> "Chinese Simplified";
            case "zht", "ct", "traditional chinese" -> "Chinese Traditional";
            default -> safe(value).trim();
        };
    }

    private static void line(StringBuilder csv, List<String> values) {
        List<String> cells = new ArrayList<>();
        for (String value : values) {
            cells.add(cell(value));
        }
        csv.append(String.join(",", cells)).append("\r\n");
    }

    /** Comillas cuando hace falta, y sin fórmulas: una celda que empieza con = + - @ se abre como texto. */
    static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0 && !text.matches("-?\\d+(\\.\\d+)?")) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
