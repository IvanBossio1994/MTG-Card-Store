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

    public void clearServiceAccountEmailCache() {
        googleSheetsService.clearServiceAccountEmailCache();
    }

    public void updateInventoryRow(int rowIndex, InventoryCard card) throws Exception {
        googleSheetsService.updateInventoryRow(rowIndex, card);
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
