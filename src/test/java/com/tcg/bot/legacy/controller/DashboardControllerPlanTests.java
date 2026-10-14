package com.tcg.bot.legacy.controller;

import com.tcg.bot.legacy.model.CardReservation;
import com.tcg.bot.legacy.model.InventoryCard;
import com.tcg.bot.legacy.service.CardKingdomApiService;
import com.tcg.bot.legacy.service.InventoryService;
import com.tcg.bot.legacy.service.PlanService;
import com.tcg.bot.legacy.service.StoreSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardControllerPlanTests {

    // ---- Los registros no dependen del plan ni de la sesion ----

    @Test
    void saleIsRecordedEvenWithoutUnlockedSession() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = controller(inventoryService, limitedPlan());
        InventoryCard card = stockCard("2", 10);
        when(inventoryService.getInventoryCards()).thenReturn(List.of(card));
        when(inventoryService.getReservationsIfSheetExists()).thenReturn(List.of());

        ResponseEntity<?> response = controller.updateQuantity(10, -1, false, true, anonymousRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(inventoryService).appendMovement(any());
        verify(inventoryService).appendCashSale(anyString(), anyString(), any(), eq(1));
    }

    // ---- Reservas: lo existente se gestiona siempre, lo nuevo requiere plan ----

    @Test
    void creatingReservationIsRejectedWhenPlanIsLimited() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = controller(inventoryService, limitedPlan());

        ResponseEntity<?> response = controller.createReservationFromSearch(
                "",
                "Sol Ring", "Commander Masters", "CMM", "0410", "nonfoil", "NM", "1",
                "Sofi", "111", "222", "2026-10-20", false, "", false, 10, false,
                anonymousRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(inventoryService, never()).appendReservation(any());
        verify(inventoryService, never()).appendReservations(any());
    }

    @Test
    void creatingReservationIsAllowedWithoutPasswordWhileUnderTheLimit() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = controller(inventoryService, freePlanUnderLimit());
        when(inventoryService.getInventoryCards()).thenReturn(List.of());
        when(inventoryService.getReservations()).thenReturn(List.of());
        when(inventoryService.getReservationsIfSheetExists()).thenReturn(List.of());

        ResponseEntity<?> response = controller.createReservationFromSearch(
                "",
                "Sol Ring", "Commander Masters", "CMM", "0410", "nonfoil", "NM", "1",
                "Sofi", "111", "222", "2026-10-20", false, "", false, 0, false,
                anonymousRequest()
        );

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deliveringExistingReservationIsAllowedWhenPlanIsLimited() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = controller(inventoryService, limitedPlan());
        InventoryCard card = stockCard("1", 10);
        card.setAction("Reservada");
        CardReservation reservation = reservedReservation("RSV-1");
        when(inventoryService.getReservations()).thenReturn(List.of(reservation));
        when(inventoryService.getInventoryCards()).thenReturn(new ArrayList<>(List.of(card)));
        when(inventoryService.getReservationsIfSheetExists()).thenReturn(List.of());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.deliverReservation("RSV-1", false, anonymousRequest(), redirect);

        assertThat(redirect.getFlashAttributes().get("error")).isNull();
        verify(inventoryService).appendCashSale(anyString(), anyString(), any(), eq(1));
    }

    @Test
    void bulkReservationIsRejectedWhenPlanIsLimited() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = controller(inventoryService, limitedPlan());
        when(inventoryService.getReservations()).thenReturn(List.of());
        when(inventoryService.getInventoryCards()).thenReturn(List.of());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.confirmBulkReservation(
                "1 Sol Ring", List.of("CMM-0410|1"), "Sofi", "111", "222", "2026-10-20",
                false, "", false, false, anonymousRequest(), redirect, new ExtendedModelMap()
        );

        assertThat(redirect.getFlashAttributes().get("error")).asString().contains("premium");
        verify(inventoryService, never()).appendReservations(any());
    }

    // ---- Clientes ----

    @Test
    void savingClientIsRejectedWhenPlanIsLimited() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = controller(inventoryService, limitedPlan());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.saveClient("Sofi", "Perez", "3666666", "3777777", "sofi@mail.com", "", "0", anonymousRequest(), redirect);

        assertThat(redirect.getFlashAttributes().get("error")).asString().contains("premium");
        verify(inventoryService, never()).upsertReservationClient(any(com.tcg.bot.legacy.model.ReservationClient.class));
    }

    @Test
    void savingClientWorksWithoutPasswordWhileUnderTheLimit() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = controller(inventoryService, freePlanUnderLimit());
        when(inventoryService.getReservationClients()).thenReturn(List.of());
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        controller.saveClient("Sofi", "Perez", "3666666", "3777777", "sofi@mail.com", "", "0", anonymousRequest(), redirect);

        verify(inventoryService).upsertReservationClient(any(com.tcg.bot.legacy.model.ReservationClient.class));
    }

    // ---- Importar lista ----

    @Test
    void importingListIsRejectedWhenPlanIsLimited() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = controller(inventoryService, limitedPlan());
        ExtendedModelMap model = new ExtendedModelMap();

        controller.confirmImportList("1 Sol Ring", List.of("CMM-0410|1"), false,
                anonymousRequest(), new RedirectAttributesModelMap(), model);

        verify(inventoryService, never()).updateQuantities(any());
        verify(inventoryService, never()).appendInventoryCards(any());
    }

    // ---- Sincronizacion ----

    @Test
    void automaticSyncIsSkippedWhenPlanIsLimited() {
        InventoryService inventoryService = mock(InventoryService.class);
        CardKingdomApiService cardKingdom = mock(CardKingdomApiService.class);
        StoreSettingsService storeSettings = mock(StoreSettingsService.class);
        when(storeSettings.hasSpreadsheetConfigured()).thenReturn(true);
        when(inventoryService.hasGoogleConnection()).thenReturn(true);
        DashboardController controller = new DashboardController(
                inventoryService, cardKingdom, null, null, mock(com.tcg.bot.legacy.service.PricingSettingsService.class), storeSettings, limitedPlan()
        );

        controller.synchronizeInventoryAutomatically();

        verify(cardKingdom, never()).getPriceList(anyBoolean());
    }

    @Test
    void manualSyncIsRejectedWhenDailyAllowanceIsUsed() {
        InventoryService inventoryService = mock(InventoryService.class);
        CardKingdomApiService cardKingdom = mock(CardKingdomApiService.class);
        StoreSettingsService storeSettings = mock(StoreSettingsService.class);
        PlanService plan = limitedPlan();
        when(plan.tryRegisterManualSync()).thenReturn(false);
        when(storeSettings.hasSpreadsheetConfigured()).thenReturn(true);
        when(inventoryService.hasGoogleConnection()).thenReturn(true);
        DashboardController controller = new DashboardController(
                inventoryService, cardKingdom, null, null, mock(com.tcg.bot.legacy.service.PricingSettingsService.class), storeSettings, plan
        );
        ExtendedModelMap model = new ExtendedModelMap();

        controller.updateInventory(model, anonymousRequest());

        verify(cardKingdom, never()).getPriceList(anyBoolean());
        assertThat(model.get("error")).asString().contains("una vez por dia");
    }

    // ---- Movimientos: en modo reducido solo el dia ----

    @Test
    void limitedPlanShowsOnlyTodayInMovementsAndHidesReport() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = controller(inventoryService, limitedPlan());
        when(inventoryService.getRecentMovements()).thenReturn(List.of());
        when(inventoryService.getCashRegisterEntries()).thenReturn(List.of());
        when(inventoryService.getPointMovements()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        controller.movements("2026-01-01", "2026-01-01", null, "true", "true", null, "report", anonymousRequest(), model);

        String today = java.time.LocalDate.now(java.time.ZoneId.of("America/Buenos_Aires")).toString();
        assertThat(model.get("movementsLocked")).isEqualTo(false);
        assertThat(model.get("historyLocked")).isEqualTo(true);
        assertThat(model.get("selectedMovementDate")).isEqualTo(today);
        assertThat(model.get("selectedCashDate")).isEqualTo(today);
        assertThat((List<?>) model.get("cashReportMonths")).isEmpty();
        assertThat(model.get("activeTab")).isEqualTo("movements");
    }

    // ---- Simulacion del plan pago ----

    @Test
    void correctPasswordActivatesPremiumSimulation() {
        PlanService plan = limitedPlan();
        DashboardController controller = controller(mock(InventoryService.class), plan);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(true)).thenReturn(mock(jakarta.servlet.http.HttpSession.class));

        controller.unlockMovements("counterspell", "/", request, new RedirectAttributesModelMap());

        verify(plan).activatePremiumSimulation();
    }

    @Test
    void modelFlagsReflectThePlanInsteadOfTheSession() {
        DashboardController controller = controller(mock(InventoryService.class), limitedPlan());

        assertThat(controller.reservationsUnlocked(anonymousRequest())).isFalse();
        assertThat(controller.movementsUnlocked(anonymousRequest())).isFalse();

        DashboardController underLimit = controller(mock(InventoryService.class), freePlanUnderLimit());
        assertThat(underLimit.reservationsUnlocked(anonymousRequest())).isTrue();
    }

    // ---- Helpers ----

    private DashboardController controller(InventoryService inventoryService, PlanService plan) {
        return new DashboardController(
                inventoryService,
                mock(CardKingdomApiService.class),
                null,
                null,
                mock(com.tcg.bot.legacy.service.PricingSettingsService.class),
                mock(StoreSettingsService.class),
                plan
        );
    }

    private PlanService limitedPlan() {
        PlanService plan = mock(PlanService.class);
        when(plan.isLimited()).thenReturn(true);
        when(plan.canUse(any())).thenReturn(false);
        when(plan.recordedSales()).thenReturn(PlanService.FREE_SALES_LIMIT);
        when(plan.salesLimit()).thenReturn(PlanService.FREE_SALES_LIMIT);
        return plan;
    }

    private PlanService freePlanUnderLimit() {
        PlanService plan = mock(PlanService.class);
        when(plan.isLimited()).thenReturn(false);
        when(plan.canUse(any())).thenReturn(true);
        when(plan.tryRegisterManualSync()).thenReturn(true);
        when(plan.recordedSales()).thenReturn(10);
        when(plan.salesLimit()).thenReturn(PlanService.FREE_SALES_LIMIT);
        return plan;
    }

    private HttpServletRequest anonymousRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getSession(false)).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/");
        return request;
    }

    private InventoryCard stockCard(String quantity, int rowIndex) {
        InventoryCard card = new InventoryCard();
        card.setName("Sol Ring");
        card.setSetName("Commander Masters");
        card.setSetCode("CMM");
        card.setCollectorNumber("0410");
        card.setPrinting("nonfoil");
        card.setCondition("NM");
        card.setQuantity(quantity);
        card.setLocalPrice("1000");
        card.setAction("En Stock");
        card.setRowIndex(rowIndex);
        return card;
    }

    private CardReservation reservedReservation(String id) {
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
        reservation.setQuantity("1");
        reservation.setClient("Sofi");
        reservation.setPhone("111");
        reservation.setDni("222");
        return reservation;
    }
}
