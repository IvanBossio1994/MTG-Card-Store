package com.tcg.bot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PriceComparisonServiceTest {

    @TempDir
    Path dir;

    private PriceComparisonService service(double rate, double multiple, double markup, double minimum) throws Exception {
        PricingSettingsService settings = new PricingSettingsService(rate, multiple, dir.toString());
        settings.update(rate, multiple, markup, minimum);
        return new PriceComparisonService(settings);
    }

    @Test
    void appliesRateAndRoundsUp() throws Exception {
        assertThat(service(1650, 500, 0, 0).calculateLocalPrice(2.49)).isEqualTo(4500);
    }

    @Test
    void appliesMarkup() throws Exception {
        // 2.49 * 1650 * 1.2 = 4930.2 -> 5000
        assertThat(service(1650, 500, 20, 0).calculateLocalPrice(2.49)).isEqualTo(5000);
    }

    @Test
    void dollarStoreRoundsToCents() throws Exception {
        com.tcg.bot.i18n.Money.setCurrency("USD");
        try {
            // 2.49 * 1 * 1.3 = 3.237 -> 3.25 (multiplo de 0.25)
            assertThat(service(1, 0.25, 30, 0).calculateLocalPrice(2.49)).isEqualTo(3.25);
            // 4.50 exacto no sube un escalon por error de coma flotante.
            assertThat(service(1, 0.25, 0, 0).calculateLocalPrice(4.5)).isEqualTo(4.5);
            assertThat(service(1, 0.01, 0, 0).calculateLocalPrice(2.494)).isEqualTo(2.5);
        } finally {
            com.tcg.bot.i18n.Money.setCurrency("ARS");
        }
    }

    @Test
    void neverBelowMinimum() throws Exception {
        assertThat(service(1650, 100, 0, 300).calculateLocalPrice(0.05)).isEqualTo(300);
    }

    @Test
    void settingsSurviveRestart() throws Exception {
        service(1650, 500, 15, 200);
        PricingSettingsService reloaded = new PricingSettingsService(1, 1, dir.toString());
        assertThat(reloaded.getMarkupPercent()).isEqualTo(15);
        assertThat(reloaded.getMinimumPrice()).isEqualTo(200);
        assertThat(reloaded.getCkDollarRate()).isEqualTo(1650);
    }
}
