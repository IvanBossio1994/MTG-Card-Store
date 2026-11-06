package com.tcg.bot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StoreThemeTest {

    @Test
    void normalizesValidColorsAndRejectsTheRest() {
        assertThat(StoreTheme.normalizeHex("#DC2626")).isEqualTo("#dc2626");
        assertThat(StoreTheme.normalizeHex(" 4f46e5 ")).isEqualTo("#4f46e5");
        assertThat(StoreTheme.normalizeHex("red")).isEmpty();
        assertThat(StoreTheme.normalizeHex("#fff")).isEmpty();
        assertThat(StoreTheme.normalizeHex("#123456;}body{display:none")).isEmpty();
        assertThat(StoreTheme.normalizeHex(null)).isEmpty();
    }

    @Test
    void darkColorsAreKeptAsTheyAre() {
        assertThat(StoreTheme.readableAccent("#4f46e5")).isEqualTo("#4f46e5");
    }

    @Test
    void lightColorsAreDarkenedUntilWhiteTextIsReadable() {
        String yellow = StoreTheme.readableAccent("#ffe14d");

        assertThat(yellow).isNotEqualTo("#ffe14d");
        assertThat(StoreTheme.contrastWithWhite(yellow)).isGreaterThanOrEqualTo(StoreTheme.MIN_CONTRAST_WITH_WHITE);
        assertThat(StoreTheme.contrastWithWhite(StoreTheme.readableAccent("#ffffff")))
                .isGreaterThanOrEqualTo(StoreTheme.MIN_CONTRAST_WITH_WHITE);
    }

    @Test
    void secondColorIsAnotherReadableShade() {
        String second = StoreTheme.secondAccent("#dc2626");

        assertThat(second).isNotEmpty().isNotEqualTo(StoreTheme.readableAccent("#dc2626"));
        assertThat(StoreTheme.contrastWithWhite(second)).isGreaterThanOrEqualTo(StoreTheme.MIN_CONTRAST_WITH_WHITE);
        assertThat(StoreTheme.secondAccent("nope")).isEmpty();
    }
}
