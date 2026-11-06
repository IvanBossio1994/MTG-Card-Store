package com.tcg.bot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoreSettingsServiceTest {

    @TempDir
    Path dir;

    private StoreSettingsService service() {
        return new StoreSettingsService("", dir.toString());
    }

    private static MockMultipartFile png(String name, String contentType) {
        return new MockMultipartFile("storeLogo", name, contentType, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
    }

    @Test
    void decorationIsSavedAndReadBack() throws Exception {
        StoreSettingsService settings = service();
        settings.updateDecoration("pattern", 7, true, "#DC2626");

        StoreSettingsService reloaded = service();
        assertThat(reloaded.getBackdropStyle()).isEqualTo("pattern");
        assertThat(reloaded.getBackdropIntensity()).isEqualTo(7);
        assertThat(reloaded.isUseLogoColors()).isTrue();
        assertThat(reloaded.getLogoAccent()).isEqualTo("#dc2626");
    }

    @Test
    void oddDecorationValuesFallBackToSafeOnes() throws Exception {
        StoreSettingsService settings = service();
        settings.updateDecoration("fullscreen", 99, false, "url(evil)");

        assertThat(settings.getBackdropStyle()).isEqualTo(StoreSettingsService.BACKDROP_WATERMARK);
        assertThat(settings.getBackdropIntensity()).isEqualTo(StoreSettingsService.MAX_BACKDROP_INTENSITY);
        assertThat(settings.getLogoAccent()).isEmpty();

        settings.updateDecoration("none", -3, false, null);
        assertThat(settings.getBackdropStyle()).isEqualTo(StoreSettingsService.BACKDROP_NONE);
        assertThat(settings.getBackdropIntensity()).isEqualTo(StoreSettingsService.MIN_BACKDROP_INTENSITY);
    }

    @Test
    void logoWithoutContentTypeIsAcceptedByItsExtension() throws Exception {
        StoreSettingsService settings = service();
        settings.saveLogo(png("Mi Logo.PNG", "application/octet-stream"));

        assertThat(settings.hasLogo()).isTrue();
        assertThat(settings.getLogoContentType()).isEqualTo("image/png");
        assertThat(settings.getLogoVersion()).isPositive();
        assertThatThrownBy(() -> settings.validateLogo(png("logo.gif", "image/gif")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removingTheLogoAlsoForgetsItsColor() throws Exception {
        StoreSettingsService settings = service();
        settings.saveLogo(png("logo.png", "image/png"));
        settings.updateDecoration("watermark", 4, true, "#dc2626");

        settings.removeLogo();

        assertThat(settings.hasLogo()).isFalse();
        assertThat(settings.getLogoAccent()).isEmpty();
        assertThat(Files.exists(dir.resolve("store-logo.png"))).isFalse();
    }
}
