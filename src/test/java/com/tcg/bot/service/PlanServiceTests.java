package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanServiceTests {

    private static final ZoneId ZONE = ZoneId.of("America/Buenos_Aires");

    @TempDir
    Path tempDir;

    @Test
    void freeStoreIsNotLimitedBeforeReachingTheSalesLimit() {
        PlanService plan = plan(sheetsWithCashSales(0), "2026-10-14T12:00:00Z");

        recordSales(plan, PlanService.FREE_SALES_LIMIT - 1);

        assertThat(plan.isLimited()).isFalse();
        assertThat(plan.remainingFreeSales()).isEqualTo(1);
    }

    @Test
    void freeStoreBecomesLimitedWhenReachingTheSalesLimit() {
        PlanService plan = plan(sheetsWithCashSales(0), "2026-10-14T12:00:00Z");

        recordSales(plan, PlanService.FREE_SALES_LIMIT);

        assertThat(plan.isLimited()).isTrue();
        assertThat(plan.canUse(PlanService.Feature.NEW_RESERVATIONS)).isFalse();
        assertThat(plan.remainingFreeSales()).isZero();
    }

    @Test
    void recordedSalesSurviveRestart() {
        GoogleSheetsService sheets = sheetsWithCashSales(0);
        recordSales(plan(sheets, "2026-10-14T12:00:00Z"), 40);

        PlanService restarted = plan(sheets, "2026-10-14T12:00:00Z");

        assertThat(restarted.recordedSales()).isEqualTo(40);
    }

    @Test
    void salesAlreadyInCashSheetCountTowardsTheLimit() {
        PlanService plan = plan(sheetsWithCashSales(120), "2026-10-14T12:00:00Z");

        assertThat(plan.recordedSales()).isEqualTo(120);
        assertThat(plan.isLimited()).isTrue();
    }

    @Test
    void deletingCashRowsDoesNotLowerTheCounter() {
        recordSales(plan(sheetsWithCashSales(0), "2026-10-14T12:00:00Z"), 80);

        PlanService afterRowsDeleted = plan(sheetsWithCashSales(3), "2026-10-14T12:00:00Z");

        assertThat(afterRowsDeleted.recordedSales()).isEqualTo(80);
    }

    @Test
    void unreadableCashSheetFallsBackToLocalCounter() throws Exception {
        GoogleSheetsService sheets = mock(GoogleSheetsService.class);
        when(sheets.hasOAuthToken()).thenReturn(true);
        when(sheets.getCashRegisterEntries()).thenThrow(new IllegalStateException("sin conexion"));
        PlanService plan = plan(sheets, "2026-10-14T12:00:00Z");

        recordSales(plan, 5);

        assertThat(plan.recordedSales()).isEqualTo(5);
    }

    @Test
    void failedCashSheetReadIsNotRetriedOnEveryCheck() throws Exception {
        GoogleSheetsService sheets = mock(GoogleSheetsService.class);
        when(sheets.hasOAuthToken()).thenReturn(true);
        when(sheets.getCashRegisterEntries()).thenThrow(new IllegalStateException("sin conexion"));
        PlanService plan = plan(sheets, "2026-10-14T12:00:00Z");

        plan.isLimited();
        plan.isLimited();
        plan.recordedSales();

        org.mockito.Mockito.verify(sheets, org.mockito.Mockito.times(1)).getCashRegisterEntries();
    }

    @Test
    void premiumStoreIsNeverLimited() {
        PlanService plan = plan(sheetsWithCashSales(500), "2026-10-14T12:00:00Z");

        plan.activatePremiumSimulation();

        assertThat(plan.isLimited()).isFalse();
        assertThat(plan.canUse(PlanService.Feature.CLIENTS)).isTrue();
    }

    @Test
    void limitedStoreCanSyncManuallyOncePerDay() {
        GoogleSheetsService sheets = sheetsWithCashSales(150);
        PlanService morning = plan(sheets, "2026-10-14T12:00:00Z");

        assertThat(morning.tryRegisterManualSync()).isTrue();
        assertThat(morning.tryRegisterManualSync()).isFalse();
        assertThat(plan(sheets, "2026-10-14T22:00:00Z").tryRegisterManualSync()).isFalse();
        assertThat(plan(sheets, "2026-10-15T12:00:00Z").tryRegisterManualSync()).isTrue();
    }

    @Test
    void storeUnderTheLimitCanSyncManuallyAnytime() {
        PlanService plan = plan(sheetsWithCashSales(0), "2026-10-14T12:00:00Z");

        assertThat(plan.tryRegisterManualSync()).isTrue();
        assertThat(plan.tryRegisterManualSync()).isTrue();
    }

    private PlanService plan(GoogleSheetsService sheets, String instant) {
        return new PlanService(sheets, tempDir.toString(), Clock.fixed(Instant.parse(instant), ZONE));
    }

    private void recordSales(PlanService plan, int count) {
        for (int index = 0; index < count; index++) {
            plan.recordSale();
        }
    }

    private GoogleSheetsService sheetsWithCashSales(int count) {
        GoogleSheetsService sheets = mock(GoogleSheetsService.class);
        List<CashRegisterEntry> entries = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            CashRegisterEntry entry = new CashRegisterEntry();
            entry.setType("VENTA");
            entries.add(entry);
        }
        try {
            when(sheets.hasOAuthToken()).thenReturn(true);
            when(sheets.getCashRegisterEntries()).thenReturn(entries);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return sheets;
    }
}
