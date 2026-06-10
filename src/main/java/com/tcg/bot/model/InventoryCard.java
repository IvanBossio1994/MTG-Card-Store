package com.tcg.bot.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InventoryCard {

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

    // ---- Guarda el indice real de la fila en Google Sheet ----
    private int rowIndex;

    // ---------------------- HELPER ---------------------- //
    public boolean isFoil() {

        return printing != null
                && printing.equalsIgnoreCase("foil");
    }
}
