package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.dto.CardKingdomProduct;
import com.tcg.bot.model.CardReservation;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.InventoryMovement;
import com.tcg.bot.model.ReservationClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class InventoryService {

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

    public boolean hasOAuthClientConfigured() {
        return googleSheetsService.hasOAuthClientConfigured();
    }

    public boolean hasOAuthToken() {
        return googleSheetsService.hasOAuthToken();
    }

    public boolean hasGoogleConnection() {
        return googleSheetsService.hasOAuthClientConfigured() && googleSheetsService.hasOAuthToken();
    }

    public void updateInventoryRow(int rowIndex, InventoryCard card) throws Exception {
        googleSheetsService.updateInventoryRow(rowIndex, card);
    }

    public void updateStockState(int rowIndex, InventoryCard card) throws Exception {
        googleSheetsService.updateStockState(rowIndex, card);
    }

    public void updateQuantity(int rowIndex, int change) throws Exception {
        googleSheetsService.updateQuantity(rowIndex, change);
    }

    public void deleteInventoryRow(int rowIndex) throws Exception {
        googleSheetsService.deleteInventoryRow(rowIndex);
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

    public void appendCashSale(String date, String time, InventoryCard card, int quantity) throws Exception {
        googleSheetsService.appendCashSale(date, time, card, quantity);
    }

    public List<CardReservation> getReservations() throws Exception {
        return googleSheetsService.getReservations();
    }

    public List<CardReservation> getReservationsIfSheetExists() throws Exception {
        return googleSheetsService.getReservationsIfSheetExists();
    }

    public void appendReservation(CardReservation reservation) throws Exception {
        googleSheetsService.appendReservation(reservation);
    }

    public void appendReservations(List<CardReservation> reservations) throws Exception {
        googleSheetsService.appendReservations(reservations);
    }

    public void updateReservationStatus(String reservationId, String status) throws Exception {
        googleSheetsService.updateReservationStatus(reservationId, status);
    }

    public void updateReservationQuantity(String reservationId, String quantity) throws Exception {
        googleSheetsService.updateReservationQuantity(reservationId, quantity);
    }

    public void updateReservationPickupDate(String reservationId, String pickupDate) throws Exception {
        googleSheetsService.updateReservationPickupDate(reservationId, pickupDate);
    }

    public void updateReservationCondition(String reservationId, String condition) throws Exception {
        googleSheetsService.updateReservationCondition(reservationId, condition);
    }

    public void updateReservationInventoryMatch(
            String reservationId,
            String setName,
            String setCode,
            String collectorNumber,
            String printing,
            String condition
    ) throws Exception {
        googleSheetsService.updateReservationInventoryMatch(
                reservationId,
                setName,
                setCode,
                collectorNumber,
                printing,
                condition
        );
    }

    public void updateReservationAssignment(
            int rowIndex,
            String status,
            String setName,
            String setCode,
            String collectorNumber,
            String printing,
            String condition
    ) throws Exception {
        googleSheetsService.updateReservationAssignment(
                rowIndex,
                status,
                setName,
                setCode,
                collectorNumber,
                printing,
                condition
        );
    }

    public void deleteReservationRows(List<Integer> rowIndexes) throws Exception {
        googleSheetsService.deleteReservationRows(rowIndexes);
    }

    public List<ReservationClient> getReservationClients() throws Exception {
        return googleSheetsService.getReservationClients();
    }

    public void upsertReservationClient(String client, String phone, String dni, String updatedAt) throws Exception {
        googleSheetsService.upsertReservationClient(client, phone, dni, updatedAt);
    }
}
