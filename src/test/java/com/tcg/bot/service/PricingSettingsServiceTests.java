package com.tcg.bot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PricingSettingsServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void storesDecimalPesosPerPointAndCalculatesPointsCost() throws Exception {
        PricingSettingsService service = new PricingSettingsService(1650.0, 500, 1.0, tempDir.toString());

        service.update(1700.0, 100, 2.5);

        PricingSettingsService reloaded = new PricingSettingsService(1650.0, 500, 1.0, tempDir.toString());
        assertThat(reloaded.getPesosPerPoint()).isEqualTo(2.5);
        assertThat(reloaded.pointsCostForPrice(10)).isEqualTo(4);
        assertThat(reloaded.pointsCostForPrice(11.24)).isEqualTo(5);
    }
}
