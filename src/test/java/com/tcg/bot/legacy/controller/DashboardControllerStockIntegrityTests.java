package com.tcg.bot.legacy.controller;

import com.tcg.bot.legacy.dto.CardKingdomPriceListResponse;
import com.tcg.bot.legacy.dto.CardKingdomProduct;
import com.tcg.bot.legacy.model.CardReservation;
import com.tcg.bot.legacy.model.InventoryCard;
import com.tcg.bot.legacy.model.InventoryMovement;
import com.tcg.bot.legacy.service.CardKingdomApiService;
import com.tcg.bot.legacy.service.InventoryService;
import com.tcg.bot.legacy.service.PriceComparisonService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardControllerStockIntegrityTests {

    // ---- Fila desactualizada (la hoja se reordeno o cambio) ----

    @Test
    void stockUpdateRejectsRowThatNoLongerHoldsTheExpectedCard() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        InventoryCard otherCard = stockCard("Arcane Signet", "BLC", "0305", "nonfoil", "NM", "2", 10);
        when(inventoryService.getInventoryCards()).thenReturn(List.of(otherCard));

        ResponseEntity<?> response = controller.updateQuantity(
                10, -1, false, true, "VENTA",
                "Sol Ring", "CMM", "410", "nonfoil", "NM",
                unlockedRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(inventoryService, never()).updateStockState(anyInt(), any());
        verify(inventoryService, never()).appendMovement(any());
        verify(inventoryService, never()).appendCashSale(anyString(), anyString(), any(), anyInt());
    }

    @Test
    void stockUpdateRejectsSameNameFromAnotherPrinting() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        InventoryCard otherPrinting = stockCard("Sol Ring", "C21", "0263", "nonfoil", "NM", "2", 10);
        when(inventoryService.getInventoryCards()).thenReturn(List.of(otherPrinting));

        ResponseEntity<?> response = controller.updateQuantity(
                10, -1, false, true, "VENTA",
                "Sol Ring", "CMM", "410", "nonfoil", "NM",
                unlockedRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(inventoryService, never()).updateStockState(anyInt(), any());
    }

    @Test
    void stockUpdateAcceptsMatchingCardWithEquivalentFormatting() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        InventoryCard card = stockCard("Sol Ring", "CMM", "0410", "No Foil", "Near Mint", "2", 10);
        when(inventoryService.getInventoryCards()).thenReturn(List.of(card));
        when(inventoryService.getReservationsIfSheetExists()).thenReturn(List.of());

        ResponseEntity<?> response = controller.updateQuantity(
                10, -1, false, true, "VENTA",
                "sol ring", "cmm", "410", "nonfoil", "NM",
                unlockedRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(inventoryService).updateStockState(eq(10), any());
    }

    // ---- Motivo de la baja ----

    @Test
    void saleDecreaseWritesSaleMovementAndCash() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        InventoryCard card = stockCard("Sol Ring", "CMM", "0410", "nonfoil", "NM", "2", 10);
        when(inventoryService.getInventoryCards()).thenReturn(List.of(card));
        when(inventoryService.getReservationsIfSheetExists()).thenReturn(List.of());

        ResponseEntity<?> response = controller.updateQuantity(
                10, -1, false, true, "VENTA", null, null, null, null, null, unlockedRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<InventoryMovement> movement = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryService).appendMovement(movement.capture());
        assertThat(movement.getValue().getSource()).isEqualTo("Unidad vendida");
        verify(inventoryService).appendCashSale(anyString(), anyString(), any(), eq(1));
    }

    @Test
    void adjustmentDecreaseWritesAdjustmentMovementWithoutCash() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        InventoryCard card = stockCard("Sol Ring", "CMM", "0410", "nonfoil", "NM", "2", 10);
        when(inventoryService.getInventoryCards()).thenReturn(List.of(card));
        when(inventoryService.getReservationsIfSheetExists()).thenReturn(List.of());

        ResponseEntity<?> response = controller.updateQuantity(
                10, -1, false, true, "AJUSTE", null, null, null, null, null, unlockedRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ArgumentCaptor<InventoryMovement> movement = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryService).appendMovement(movement.capture());
        assertThat(movement.getValue().getSource()).isEqualTo("Ajuste de conteo");
        assertThat(movement.getValue().getQuantity()).isEqualTo("-1");
        verify(inventoryService, never()).appendCashSale(anyString(), anyString(), any(), anyInt());
    }

    @Test
    void lossDecreaseWritesLossMovementWithoutCash() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        InventoryCard card = stockCard("Sol Ring", "CMM", "0410", "nonfoil", "NM", "2", 10);
        when(inventoryService.getInventoryCards()).thenReturn(List.of(card));
        when(inventoryService.getReservationsIfSheetExists()).thenReturn(List.of());

        controller.updateQuantity(10, -1, false, true, "PERDIDA", null, null, null, null, null, unlockedRequest());

        ArgumentCaptor<InventoryMovement> movement = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(inventoryService).appendMovement(movement.capture());
        assertThat(movement.getValue().getSource()).isEqualTo("Perdida o dano");
        verify(inventoryService, never()).appendCashSale(anyString(), anyString(), any(), anyInt());
    }

    @Test
    void unknownDecreaseReasonIsRejectedWithoutTouchingStock() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        InventoryCard card = stockCard("Sol Ring", "CMM", "0410", "nonfoil", "NM", "2", 10);
        when(inventoryService.getInventoryCards()).thenReturn(List.of(card));

        ResponseEntity<?> response = controller.updateQuantity(
                10, -1, false, true, "REGALO_RARO", null, null, null, null, null, unlockedRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(inventoryService, never()).updateStockState(anyInt(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportDoesNotCountStockAdjustmentsAsSales() throws Exception {
        DashboardController controller = new DashboardController(null, null, null, null, null, null);
        Method method = DashboardController.class.getDeclaredMethod(
                "cashReportMonths", List.class, List.class, List.class
        );
        method.setAccessible(true);
        List<InventoryMovement> movements = List.of(
                movement("2026-07-01", "-1", "Unidad vendida"),
                movement("2026-07-01", "-2", "Ajuste de conteo"),
                movement("2026-07-01", "-1", "Perdida o dano"),
                movement("2026-07-01", "-1", "Uso interno")
        );

        List<DashboardController.CashReportMonth> reports =
                (List<DashboardController.CashReportMonth>) method.invoke(controller, List.of(), movements, List.of());

        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).soldQuantity()).isEqualTo(1);
    }

    // ---- La sincronizacion no borra precios ----

    @Test
    void syncKeepsLocalPriceWhenCardKingdomHasNoMatch() throws Exception {
        CardKingdomApiService cardKingdomApiService = mock(CardKingdomApiService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(
                inventoryService, cardKingdomApiService, null, mock(PriceComparisonService.class), null, null
        );
        InventoryCard card = stockCard("Carta Rara", "XXX", "0001", "nonfoil", "NM", "1", 10);
        card.setLocalPrice("5000");
        card.setCkPriceUsd("3.00");
        CardKingdomPriceListResponse priceList = new CardKingdomPriceListResponse();
        priceList.setData(List.of());
        when(inventoryService.getReservationsIfSheetExists()).thenReturn(List.of());
        when(cardKingdomApiService.findProduct(eq(card), eq(priceList))).thenReturn(null);

        invokeUpdateCard(controller, card, priceList);

        assertThat(card.getLocalPrice()).isEqualTo("5000");
    }

    @Test
    void syncKeepsLocalPriceWhenCardKingdomHasNoPriceForCondition() throws Exception {
        CardKingdomApiService cardKingdomApiService = mock(CardKingdomApiService.class);
        PriceComparisonService priceComparisonService = mock(PriceComparisonService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(
                inventoryService, cardKingdomApiService, null, priceComparisonService, null, null
        );
        InventoryCard card = stockCard("Sol Ring", "CMM", "0410", "nonfoil", "G", "1", 10);
        card.setLocalPrice("2500");
        card.setCkPriceUsd("1.50");
        CardKingdomProduct product = new CardKingdomProduct();
        product.setName("Sol Ring");
        product.setSku("CMM-0410");
        CardKingdomPriceListResponse priceList = new CardKingdomPriceListResponse();
        priceList.setData(List.of(product));
        when(inventoryService.getReservationsIfSheetExists()).thenReturn(List.of());
        when(cardKingdomApiService.findProduct(eq(card), eq(priceList))).thenReturn(product);
        when(priceComparisonService.getBestConditionPrice(eq(product), eq(card))).thenReturn(null);

        invokeUpdateCard(controller, card, priceList);

        assertThat(card.getLocalPrice()).isEqualTo("2500");
    }

    // ---- Cancelar una reserva no inventa stock ----

    @Test
    @SuppressWarnings("unchecked")
    void cancellingReservedCardWithZeroStockKeepsStockAtZero() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        InventoryCard soldOut = stockCard("Sol Ring", "CMM", "0410", "nonfoil", "NM", "0", 10);
        soldOut.setSetName("Commander Masters");
        CardReservation reservation = reservedReservation("RSV-9", 1);
        when(inventoryService.getReservations()).thenReturn(List.of(reservation));
        when(inventoryService.getInventoryCards()).thenReturn(new ArrayList<>(List.of(soldOut)));

        controller.deleteReservation("RSV-9", unlockedRequest(), mock(RedirectAttributes.class));

        verify(inventoryService, never()).appendInventoryCard(any());
        assertThat(soldOut.getQuantity()).isEqualTo("0");
        ArgumentCaptor<Map<Integer, InventoryCard>> rows = ArgumentCaptor.forClass(Map.class);
        verify(inventoryService, org.mockito.Mockito.atLeastOnce()).updateInventoryRows(rows.capture());
        rows.getAllValues().stream()
                .filter(written -> written.containsKey(10))
                .forEach(written -> assertThat(written.get(10).getAction()).isEqualTo("Sin Stock"));
        ArgumentCaptor<List<InventoryMovement>> movements = ArgumentCaptor.forClass(List.class);
        verify(inventoryService).appendMovements(movements.capture());
        assertThat(movements.getValue()).hasSize(1);
        assertThat(movements.getValue().get(0).getSource()).isEqualTo("Pedido cancelado");
        assertThat(movements.getValue().get(0).getPreviousStock()).isEqualTo("0");
        assertThat(movements.getValue().get(0).getNewStock()).isEqualTo("0");
    }

    @Test
    void cancellingReservedCardWhoseRowWasDeletedDoesNotRecreateStock() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        CardReservation reservation = reservedReservation("RSV-10", 2);
        when(inventoryService.getReservations()).thenReturn(List.of(reservation));
        when(inventoryService.getInventoryCards()).thenReturn(new ArrayList<>());

        controller.deleteReservation("RSV-10", unlockedRequest(), mock(RedirectAttributes.class));

        verify(inventoryService, never()).appendInventoryCard(any());
        verify(inventoryService).deleteReservationRows(any());
    }

    // ---- Helpers ----

    private void invokeUpdateCard(
            DashboardController controller,
            InventoryCard card,
            CardKingdomPriceListResponse priceList
    ) throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "updateCard", int.class, InventoryCard.class, CardKingdomPriceListResponse.class, List.class
        );
        method.setAccessible(true);
        method.invoke(controller, card.getRowIndex(), card, priceList, List.of(card));
    }

    private HttpServletRequest unlockedRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("movementsAccessUnlocked")).thenReturn(Boolean.TRUE);
        return request;
    }

    private InventoryCard stockCard(
            String name,
            String setCode,
            String collectorNumber,
            String printing,
            String condition,
            String quantity,
            int rowIndex
    ) {
        InventoryCard card = new InventoryCard();
        card.setName(name);
        card.setSetName("Commander Masters");
        card.setSetCode(setCode);
        card.setCollectorNumber(collectorNumber);
        card.setPrinting(printing);
        card.setCondition(condition);
        card.setQuantity(quantity);
        card.setLocalPrice("1000");
        card.setAction("En Stock");
        card.setRowIndex(rowIndex);
        return card;
    }

    private CardReservation reservedReservation(String id, int quantity) {
        CardReservation reservation = new CardReservation();
        reservation.setId(id);
        reservation.setRowIndex(5);
        reservation.setStatus(CardReservation.STATUS_RESERVED);
        reservation.setName("Sol Ring");
        reservation.setSetName("Commander Masters");
        reservation.setSetCode("CMM");
        reservation.setCollectorNumber("0410");
        reservation.setPrinting("nonfoil");
        reservation.setCondition("NM");
        reservation.setQuantity(String.valueOf(quantity));
        reservation.setClient("Sofi");
        reservation.setDni("222");
        return reservation;
    }

    private InventoryMovement movement(String date, String quantity, String source) {
        return new InventoryMovement(
                date + " 10:00:00", date, "10:00", "SALIDA", quantity, "Sol Ring",
                "Commander Masters", "CMM", "0410", "nonfoil", "3", "2", source
        );
    }
}
