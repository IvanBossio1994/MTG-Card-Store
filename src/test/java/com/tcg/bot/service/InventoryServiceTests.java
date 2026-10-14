package com.tcg.bot.service;

import com.tcg.bot.model.InventoryCard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class InventoryServiceTests {

    @Test
    void cashSaleCountsTowardsThePlanSalesLimit() throws Exception {
        GoogleSheetsService sheets = mock(GoogleSheetsService.class);
        PlanService plan = mock(PlanService.class);
        InventoryService inventoryService = new InventoryService(sheets, plan);
        InventoryCard card = new InventoryCard();

        inventoryService.appendCashSale("2026-10-14", "10:00:00", card, 2);

        verify(sheets).appendCashSale("2026-10-14", "10:00:00", card, 2);
        verify(plan).recordSale();
    }

    @Test
    void failedCashSaleDoesNotCountTowardsTheLimit() throws Exception {
        GoogleSheetsService sheets = mock(GoogleSheetsService.class);
        PlanService plan = mock(PlanService.class);
        InventoryService inventoryService = new InventoryService(sheets, plan);
        doThrow(new IllegalStateException("sin conexion"))
                .when(sheets).appendCashSale(anyString(), anyString(), any(), anyInt());

        assertThatThrownBy(() -> inventoryService.appendCashSale("2026-10-14", "10:00:00", new InventoryCard(), 1))
                .isInstanceOf(IllegalStateException.class);

        verify(plan, never()).recordSale();
    }
}
