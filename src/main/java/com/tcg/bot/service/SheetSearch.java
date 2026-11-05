package com.tcg.bot.service;

import com.tcg.bot.model.InventoryCard;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Busca en el Sheet de Magic las cartas que coinciden con la búsqueda. Sirve para mostrar las que
 * Card Kingdom no tiene (cartas raras, promos, ediciones que CK no lista), que de otro modo no se
 * podrían sumar al carrito.
 */
public final class SheetSearch {

    private SheetSearch() {
    }

    /**
     * Cartas del Sheet que coinciden con nombre, edición y número, y que {@code alreadyShown} no cubre.
     * El nombre coincide si contiene todas las palabras buscadas (sin importar mayúsculas ni tildes).
     */
    public static List<InventoryCard> find(List<InventoryCard> cards, String query, String set, String number,
                                           Predicate<InventoryCard> alreadyShown) {
        List<String> words = words(query);
        String setWanted = normalize(set);
        String numberWanted = number(number);
        if (words.isEmpty() && setWanted.isEmpty() && numberWanted.isEmpty()) {
            return List.of();
        }

        return cards.stream()
                .filter(card -> card.getGame() == null)
                .filter(card -> card.getRowIndex() > 0 && card.getName() != null && !card.getName().isBlank())
                .filter(card -> {
                    String name = normalize(card.getName());
                    return words.stream().allMatch(name::contains);
                })
                .filter(card -> setWanted.isEmpty()
                        || normalize(card.getSetName()).contains(setWanted)
                        || normalize(card.getSetCode()).equals(setWanted))
                .filter(card -> numberWanted.isEmpty() || number(card.getCollectorNumber()).equals(numberWanted))
                .filter(alreadyShown.negate())
                .toList();
    }

    private static List<String> words(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(normalized.split("\\s+")).filter(word -> !word.isBlank()).toList();
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String plain = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    /** "036" y "36" son el mismo número. */
    private static String number(String value) {
        String normalized = normalize(value).replace(" ", "");
        String stripped = normalized.replaceFirst("^0+(?=.)", "");
        return stripped;
    }
}
