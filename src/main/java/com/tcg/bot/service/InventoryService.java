package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.dto.CardKingdomProduct;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.InventoryMovement;
import com.tcg.bot.model.Reservation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class InventoryService implements InventoryStore {

    @Override
    public java.util.List<com.tcg.bot.model.PricePoint> getPriceHistory() throws Exception {
        return googleSheetsService.getPriceHistory();
    }

    @Override
    public void appendPriceHistory(java.util.List<com.tcg.bot.model.PricePoint> points) throws Exception {
        googleSheetsService.appendPriceHistory(points);
    }


    private final GoogleSheetsService googleSheetsService;

    public InventoryService(GoogleSheetsService googleSheetsService) {
        this.googleSheetsService = googleSheetsService;
    }

    public List<InventoryCard> getInventoryCards() throws Exception {
        return googleSheetsService.getInventoryCards();
    }

    public void prepareInventorySheet(List<CardKingdomProduct> products) throws Exception {
        googleSheetsService.prepareInventorySheet(products);
    }

    public String getServiceAccountEmail() {
        return googleSheetsService.getServiceAccountEmail();
    }

    public boolean usesGoogleAccount() {
        return googleSheetsService.usesGoogleAccount();
    }

    public boolean hasCredentialsConfigured() {
        return googleSheetsService.hasCredentialsConfigured();
    }

    public String getConfiguredCredentialsPath() {
        return googleSheetsService.getConfiguredCredentialsPath();
    }

    /** Vuelve a leer todo del Sheet en la próxima consulta (por ejemplo, al sincronizar). */
    public void clearReadCache() {
        googleSheetsService.clearReadCache();
    }

    public void clearServiceAccountEmailCache() {
        googleSheetsService.clearServiceAccountEmailCache();
    }

    public void updateInventoryRow(int rowIndex, InventoryCard card) throws Exception {
        googleSheetsService.updateInventoryRow(rowIndex, card);
    }

    @Override
    public java.util.List<com.tcg.bot.model.GameCard> getGameCards(com.tcg.bot.model.Game game) throws Exception {
        var rows = googleSheetsService.getTabRows(game.sheetName(), com.tcg.bot.model.Game.HEADER);
        java.util.List<com.tcg.bot.model.GameCard> cards = new java.util.ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            if (!rows.get(index).get(1).isBlank()) {
                cards.add(com.tcg.bot.model.GameCard.fromRow(rows.get(index), index + 2));
            }
        }
        return cards;
    }

    @Override
    public int appendGameCard(com.tcg.bot.model.Game game, com.tcg.bot.model.GameCard card) throws Exception {
        return googleSheetsService.appendTabRow(game.sheetName(), com.tcg.bot.model.Game.HEADER, card.toRow());
    }

    @Override
    public void updateGameCard(com.tcg.bot.model.Game game, com.tcg.bot.model.GameCard card) throws Exception {
        googleSheetsService.updateTabRow(game.sheetName(), com.tcg.bot.model.Game.HEADER, card.getRowIndex(), card.toRow());
    }

    @Override
    public java.util.List<com.tcg.bot.model.CreditEntry> getCreditEntries() throws Exception {
        return googleSheetsService.getCreditEntries();
    }

    @Override
    public void appendCreditEntry(com.tcg.bot.model.CreditEntry entry) throws Exception {
        googleSheetsService.appendCreditEntry(entry);
    }

    @Override
    public void updateCardIdentity(int rowIndex, InventoryCard card) throws Exception {
        googleSheetsService.updateCardIdentity(rowIndex, card);
    }

    public void updateStockState(int rowIndex, InventoryCard card) throws Exception {
        googleSheetsService.updateStockState(rowIndex, card);
    }

    public void updateQuantity(int rowIndex, int change) throws Exception {
        googleSheetsService.updateQuantity(rowIndex, change);
    }

    @Override
    public int appendInventoryCard(InventoryCard card) throws Exception {
        return googleSheetsService.appendInventoryCard(card);
    }

    public void appendInventoryCards(List<InventoryCard> cards) throws Exception {
        googleSheetsService.appendInventoryCards(cards);
    }

    public void updateQuantities(Map<Integer, Integer> quantitiesByRow) throws Exception {
        googleSheetsService.updateQuantities(quantitiesByRow);
    }

    public void updateInventoryRows(Map<Integer, InventoryCard> cardsByRow) throws Exception {
        googleSheetsService.updateInventoryRows(cardsByRow);
    }

    public void sortInventoryByName() throws Exception {
        googleSheetsService.sortInventoryByName();
    }

    @Override
    public void updateLocalPrice(int rowIndex, String localPrice) throws Exception {
        googleSheetsService.updateLocalPrice(rowIndex, localPrice);
    }

    public List<InventoryMovement> getRecentMovements() throws Exception {
        return googleSheetsService.getRecentMovements();
    }

    public void appendMovement(InventoryMovement movement) throws Exception {
        googleSheetsService.appendMovement(movement);
    }

    public void appendMovements(List<InventoryMovement> movements) throws Exception {
        googleSheetsService.appendMovements(movements);
    }

    public List<CashRegisterEntry> getCashRegisterEntries() throws Exception {
        return googleSheetsService.getCashRegisterEntries();
    }

    public void appendCashEntry(CashRegisterEntry entry) throws Exception {
        googleSheetsService.appendCashEntry(entry);
    }

    public List<Reservation> getReservations() throws Exception {
        return googleSheetsService.getReservations();
    }

    public void appendReservations(List<Reservation> reservations) throws Exception {
        googleSheetsService.appendReservations(reservations);
    }

    public void updateReservationStatuses(List<Reservation> reservations) throws Exception {
        googleSheetsService.updateReservationStatuses(reservations);
    }
}
