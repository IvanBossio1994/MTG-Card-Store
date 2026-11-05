package com.tcg.bot.service;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.InventoryMovement;
import com.tcg.bot.model.PricePoint;
import com.tcg.bot.model.Reservation;

import java.util.List;

/**
 * Lo minimo que necesitan las operaciones de stock y caja del almacenamiento (hoy, el Google Sheet).
 * Existe para poder probar la logica sin pegarle a Google.
 */
public interface InventoryStore {

    List<InventoryCard> getInventoryCards() throws Exception;

    void updateStockState(int rowIndex, InventoryCard card) throws Exception;

    void appendMovement(InventoryMovement movement) throws Exception;

    List<CashRegisterEntry> getCashRegisterEntries() throws Exception;

    void appendCashEntry(CashRegisterEntry entry) throws Exception;

    List<Reservation> getReservations() throws Exception;

    void appendReservations(List<Reservation> reservations) throws Exception;

    /** Actualiza Estado y Actualizado de cada linea, usando su rowIndex. */
    void updateReservationStatuses(List<Reservation> reservations) throws Exception;

    /** Historial de precios (hoja "Precios"). */
    default List<PricePoint> getPriceHistory() throws Exception {
        return List.of();
    }

    default void appendPriceHistory(List<PricePoint> points) throws Exception {
    }

    /** Completa nombre, edición, número y terminación de una fila (pantalla "Completar ediciones"). */
    default void updateCardIdentity(int rowIndex, InventoryCard card) throws Exception {
        throw new UnsupportedOperationException("No disponible");
    }

    /** Crédito de tienda (hoja "Credito"). */
    default List<com.tcg.bot.model.CreditEntry> getCreditEntries() throws Exception {
        return List.of();
    }

    default void appendCreditEntry(com.tcg.bot.model.CreditEntry entry) throws Exception {
        throw new UnsupportedOperationException("No disponible");
    }

    /** Agrega una fila nueva al inventario y devuelve su número de fila. */
    default int appendInventoryCard(InventoryCard card) throws Exception {
        throw new UnsupportedOperationException("No disponible");
    }

    /** Inventario de otro juego (su propia pestaña). */
    default List<com.tcg.bot.model.GameCard> getGameCards(com.tcg.bot.model.Game game) throws Exception {
        return List.of();
    }

    default int appendGameCard(com.tcg.bot.model.Game game, com.tcg.bot.model.GameCard card) throws Exception {
        throw new UnsupportedOperationException("No disponible");
    }

    default void updateGameCard(com.tcg.bot.model.Game game, com.tcg.bot.model.GameCard card) throws Exception {
        throw new UnsupportedOperationException("No disponible");
    }

    /** Las cartas de todos los juegos: Magic y las pestañas de Pokémon y Yu-Gi-Oh (con {@code game} puesto). */
    default List<InventoryCard> getAllStockCards() throws Exception {
        List<InventoryCard> all = new java.util.ArrayList<>(getInventoryCards());
        for (com.tcg.bot.model.Game game : com.tcg.bot.model.Game.values()) {
            for (com.tcg.bot.model.GameCard card : getGameCards(game)) {
                if (card.getName() != null && !card.getName().isBlank()) {
                    all.add(card.asInventoryCard(game));
                }
            }
        }
        return all;
    }

    /** Escribe solo el precio local de una fila de Magic. */
    default void updateLocalPrice(int rowIndex, String localPrice) throws Exception {
        throw new UnsupportedOperationException("No disponible");
    }

    /** Guarda el precio local de una carta de cualquier juego en su pestaña (todas sus unidades). */
    default void updateAnyLocalPrice(InventoryCard card, double price) throws Exception {
        if (card.getGame() == null) {
            updateLocalPrice(card.getRowIndex(), com.tcg.bot.i18n.Money.plain(price));
            return;
        }
        com.tcg.bot.model.GameCard row = getGameCards(card.getGame()).stream()
                .filter(candidate -> card.getId() != null && card.getId().equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(I18n.t("games.err.cardGone", card.getName(), card.getGame().sheetName())));
        row.setLocalPrice(price);
        updateGameCard(card.getGame(), row);
    }

    /** Guarda la cantidad de una carta de cualquier juego en su pestaña. */
    default void updateAnyStock(InventoryCard card) throws Exception {
        if (card.getGame() == null) {
            updateStockState(card.getRowIndex(), card);
            return;
        }
        com.tcg.bot.model.GameCard row = getGameCards(card.getGame()).stream()
                .filter(candidate -> card.getId() != null && card.getId().equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(I18n.t("games.err.cardGone", card.getName(), card.getGame().sheetName())));
        row.setQuantity(Math.max(com.tcg.bot.util.Amounts.parseInt(card.getQuantity()), 0));
        updateGameCard(card.getGame(), row);
    }
}
