package com.tcg.bot.service;

import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.service.ImportListParser.ParsedImportLine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * "Pegar un mazo": el cliente trae su lista (Moxfield, Arena, texto) y se ve qué cartas hay en stock,
 * cuánto sale todo y qué falta. No cambia nada del Sheet: solo arma la propuesta para el carrito.
 */
@Service
public class DeckCheckService {

    /** Títulos de sección que traen las listas exportadas y no son cartas. */
    private static final Set<String> SECTION_WORDS = Set.of(
            "deck", "mazo", "main", "mainboard", "main deck", "sideboard", "side", "commander", "comandante",
            "companion", "maybeboard", "considering", "tokens", "pokemon", "pokémon", "trainer", "energy",
            "monster", "spell", "trap", "extra deck");
    private static final Pattern SECTION_WITH_COUNT = Pattern.compile("^[\\p{L} ]+\\s*\\(\\d+\\)$");

    private final InventoryStore store;

    public DeckCheckService(InventoryStore store) {
        this.store = store;
    }

    /** Una unidad (o varias) tomada de una fila del stock. */
    public record Pick(InventoryCard card, int quantity) {
        public double subtotal() {
            return card.localPriceValue() * quantity;
        }

        public String setLabel() {
            String set = card.getSetName() == null || card.getSetName().isBlank() ? card.getSetCode() : card.getSetName();
            return set == null ? "" : set;
        }
    }

    public record Line(String name, int wanted, List<Pick> picks) {
        public int found() {
            return picks.stream().mapToInt(Pick::quantity).sum();
        }

        public int missing() {
            return Math.max(wanted - found(), 0);
        }

        public double subtotal() {
            return picks.stream().mapToDouble(Pick::subtotal).sum();
        }

        /** Alguna de las cartas encontradas no tiene precio todavía. */
        public boolean missingPrice() {
            return picks.stream().anyMatch(pick -> pick.card().localPriceValue() <= 0);
        }
    }

    public record Result(List<Line> lines) {
        public int wanted() {
            return lines.stream().mapToInt(Line::wanted).sum();
        }

        public int found() {
            return lines.stream().mapToInt(Line::found).sum();
        }

        public double total() {
            return lines.stream().mapToDouble(Line::subtotal).sum();
        }

        public List<Line> complete() {
            return lines.stream().filter(line -> line.missing() == 0).toList();
        }

        public List<Line> partial() {
            return lines.stream().filter(line -> line.found() > 0 && line.missing() > 0).toList();
        }

        public List<Line> absent() {
            return lines.stream().filter(line -> line.found() == 0).toList();
        }

        /** Lo que falta, en el mismo formato de lista ("2 Sol Ring"), para copiar o mandar por WhatsApp. */
        public String missingText() {
            StringBuilder text = new StringBuilder();
            for (Line line : lines) {
                if (line.missing() > 0) {
                    text.append(line.missing()).append(' ').append(line.name()).append('\n');
                }
            }
            return text.toString().trim();
        }
    }

    public Result check(String rawList) throws Exception {
        List<ParsedImportLine> parsed = ImportListParser.parseAndMergeImportLines(rawList == null ? "" : rawList);
        List<InventoryCard> stock = new ArrayList<>(store.getAllStockCards().stream()
                .filter(card -> StockOperationsService.quantity(card) > 0)
                .toList());
        // Cada carta del stock se puede usar una sola vez entre todas las líneas.
        java.util.Map<InventoryCard, Integer> left = new java.util.IdentityHashMap<>();
        stock.forEach(card -> left.put(card, StockOperationsService.quantity(card)));

        List<Line> lines = new ArrayList<>();
        for (ParsedImportLine line : parsed) {
            if (line.quantity() <= 0 || isSectionTitle(line.name()) || isSectionTitle(line.originalLine())) {
                continue;
            }
            Set<String> names = new HashSet<>(ImportListParser.importNameCandidates(line.name()));
            List<InventoryCard> candidates = stock.stream()
                    .filter(card -> left.get(card) > 0)
                    .filter(card -> ImportListParser.importNameCandidates(card.getName()).stream().anyMatch(names::contains))
                    .sorted(preference(line))
                    .toList();

            List<Pick> picks = new ArrayList<>();
            int needed = line.quantity();
            for (InventoryCard card : candidates) {
                if (needed == 0) {
                    break;
                }
                int take = Math.min(needed, left.get(card));
                left.put(card, left.get(card) - take);
                picks.add(new Pick(card, take));
                needed -= take;
            }
            lines.add(new Line(line.name(), line.quantity(), picks));
        }
        return new Result(lines);
    }

    /**
     * Primero la edición y el número pedidos, después la terminación pedida (foil o no) y, entre iguales,
     * la más barata con precio: es la que le conviene al cliente y la que se ofrecería primero en el mostrador.
     */
    private static Comparator<InventoryCard> preference(ParsedImportLine line) {
        String set = line.setCode() == null ? "" : line.setCode().trim().toLowerCase(Locale.ROOT);
        String number = ImportListParser.normalizeImportCollector(line.collectorNumber());
        return Comparator
                .comparing((InventoryCard card) -> !set.isEmpty() && !set.equalsIgnoreCase(safe(card.getSetCode())))
                .thenComparing(card -> !number.isEmpty() && !number.equals(ImportListParser.normalizeImportCollector(card.getCollectorNumber())))
                .thenComparing(card -> line.foil() != null && line.foil() != card.isFoil())
                .thenComparing(card -> card.localPriceValue() <= 0)
                .thenComparingDouble(InventoryCard::localPriceValue);
    }

    static boolean isSectionTitle(String name) {
        String value = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (value.endsWith(":")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value.isEmpty() || SECTION_WORDS.contains(value) || SECTION_WITH_COUNT.matcher(value).matches()
                || value.startsWith("//");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
