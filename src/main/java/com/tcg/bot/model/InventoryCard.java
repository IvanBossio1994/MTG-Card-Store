package com.tcg.bot.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InventoryCard {

    /** null para Magic; el juego cuando la carta vive en la pestaña de Pokémon o Yu-Gi-Oh. No va al Sheet. */
    private Game game;

    // ---- Cantidad ----
    private String quantity;

    // ---- Nombre carta ----
    private String name;

    // ---- Código set ----
    private String setCode;

    // ---- Nombre set ----
    private String setName;

    // ---- Número carta ----
    private String collectorNumber;

    // ---- Condición ----
    private String condition;

    // ---- Foil / Non Foil ----
    private String printing;

    // ---- Idioma ----
    private String language;

    // ---- Precio local ----
    private String localPrice;

    // ---- Precio CK USD ----
    private String ckPriceUsd;

    // ---- Acción ----
    private String action;

    // ---- ID estable de la carta (columna ID del Sheet) ----
    private String id;

    // ---- Guarda el indice real de la fila en Google Sheet ----
    private int rowIndex;

    // ---------------------- HELPER ---------------------- //

    /**
     * Precio puesto a mano: tiene precio local pero Card Kingdom no da precio (columna CK vacía).
     * Solo aplica a Magic; las otras pestañas usan {@link GameCard#isManualPrice()}.
     */
    /** Precio local como número entero (0 si no tiene). */
    public long localPriceValue() {
        return Math.round(com.tcg.bot.util.Amounts.parse(localPrice));
    }

    public boolean isManualPrice() {
        return game == null
                && com.tcg.bot.util.Amounts.parse(localPrice) > 0
                && com.tcg.bot.util.Amounts.parse(ckPriceUsd) <= 0;
    }

    public boolean isFoil() {

        return printing != null
                && printing.equalsIgnoreCase("foil");
    }
}
