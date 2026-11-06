package com.tcg.bot.model;

import com.tcg.bot.util.Amounts;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Una fila de la pestaña de inventario de otro juego. */
@Getter
@Setter
public class GameCard {
    private int rowIndex;
    private int quantity;
    private String name;
    private String setName;
    private String setCode;
    private String number;
    private String rarity;
    private String finish;
    private double localPrice;
    private Double usd;
    private String id;
    private String catalogId;
    /** Costo por unidad; null si no se cargó. */
    private Double cost;
    private String location;

    public static GameCard fromRow(List<String> row, int rowIndex) {
        GameCard card = new GameCard();
        card.rowIndex = rowIndex;
        card.quantity = Math.max(Amounts.parseInt(row.get(0)), 0);
        card.name = row.get(1);
        card.setName = row.get(2);
        card.setCode = row.get(3);
        card.number = row.get(4);
        card.rarity = row.get(5);
        card.finish = row.get(6);
        card.localPrice = Amounts.parse(row.get(7));
        card.usd = Amounts.parseOrNull(row.get(8));
        card.id = row.get(9);
        card.catalogId = row.get(10);
        card.cost = row.size() > 11 ? Amounts.parseOrNull(row.get(11)) : null;
        card.location = row.size() > 12 ? row.get(12) : "";
        return card;
    }

    public List<Object> toRow() {
        List<Object> row = new ArrayList<>();
        row.add(quantity);
        row.add(safe(name));
        row.add(safe(setName));
        row.add(safe(setCode));
        row.add(safe(number));
        row.add(safe(rarity));
        row.add(safe(finish));
        row.add(localPrice > 0 ? com.tcg.bot.i18n.Money.sheetValue(localPrice) : "");
        row.add(usd == null ? "" : usd);
        row.add(safe(id));
        row.add(safe(catalogId));
        row.add(cost == null ? "" : com.tcg.bot.i18n.Money.sheetValue(cost));
        row.add(safe(location));
        return row;
    }

    public String formattedLocalPrice() {
        return localPrice > 0 ? com.tcg.bot.i18n.Money.format(localPrice) : "-";
    }

    /** Precio puesto a mano: tiene precio local pero el catálogo no da precio en dólares. */
    public boolean isManualPrice() {
        return localPrice > 0 && (usd == null || usd <= 0);
    }

    public double priceValue() {
        return com.tcg.bot.i18n.Money.round(localPrice);
    }

    /** Costo para el formulario ("1500" o "4.5"), vacío si no se sabe. */
    public String costInput() {
        return cost == null ? "" : com.tcg.bot.i18n.Money.plainNumber(cost);
    }

    public String formattedUsd() {
        return usd == null ? "-" : com.tcg.bot.i18n.Money.usd(usd);
    }

    /** Para Caja/Movimientos, que usan las columnas de Magic. */
    public InventoryCard asInventoryCard(Game game) {
        InventoryCard card = new InventoryCard();
        card.setId(id);
        card.setRowIndex(rowIndex);
        card.setName(name);
        card.setSetName(game.label() + " · " + safe(setName));
        card.setSetCode(setCode);
        card.setCollectorNumber(number);
        card.setPrinting(finish);
        card.setQuantity(String.valueOf(quantity));
        card.setLocalPrice(localPrice > 0 ? com.tcg.bot.i18n.Money.plain(localPrice) : "");
        card.setCost(cost == null ? "" : com.tcg.bot.i18n.Money.plain(cost));
        card.setLocation(safe(location));
        card.setGame(game);
        return card;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
