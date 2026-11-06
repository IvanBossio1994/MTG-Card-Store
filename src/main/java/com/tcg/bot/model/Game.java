package com.tcg.bot.model;

import java.util.Arrays;
import java.util.List;

/** Juegos además de Magic. Cada uno tiene su propia pestaña de inventario en el mismo Sheet. */
public enum Game {
    POKEMON("pokemon", "Pokémon", "Inventario Pokemon"),
    YUGIOH("yugioh", "Yu-Gi-Oh!", "Inventario YuGiOh");

    public static final List<String> HEADER = List.of(
            "Cantidad", "Nombre", "Set", "Codigo", "Numero", "Rareza", "Terminacion",
            "Precio Local", "Precio USD", "ID", "ID catalogo", "Costo", "Ubicacion");

    private final String slug;
    private final String label;
    private final String sheetName;

    Game(String slug, String label, String sheetName) {
        this.slug = slug;
        this.label = label;
        this.sheetName = sheetName;
    }

    public String slug() {
        return slug;
    }

    public String label() {
        return label;
    }

    public String sheetName() {
        return sheetName;
    }

    public static Game fromSlug(String slug) {
        return Arrays.stream(values()).filter(game -> game.slug.equalsIgnoreCase(slug)).findFirst().orElse(null);
    }

    public static boolean isGameSheet(String title) {
        return Arrays.stream(values()).anyMatch(game -> game.sheetName.equals(title));
    }
}
