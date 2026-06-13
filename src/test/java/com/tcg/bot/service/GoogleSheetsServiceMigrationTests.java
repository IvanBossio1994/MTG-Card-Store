package com.tcg.bot.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleSheetsServiceMigrationTests {

    private final GoogleSheetsService service = new GoogleSheetsService(null, "", "");

    @Test
    void normalizesImportedConditionToAppCodes() throws Exception {
        Method method = GoogleSheetsService.class.getDeclaredMethod("normalizeCondition", String.class);
        method.setAccessible(true);

        assertThat((String) method.invoke(service, "NearMint")).isEqualTo("NM");
        assertThat((String) method.invoke(service, "Mint")).isEqualTo("NM");
        assertThat((String) method.invoke(service, "Lightly Played")).isEqualTo("EX");
    }

    @Test
    void defaultsImportedLanguageToEnglishCode() throws Exception {
        Method method = GoogleSheetsService.class.getDeclaredMethod("normalizeLanguage", String.class);
        method.setAccessible(true);

        assertThat((String) method.invoke(service, "")).isEqualTo("EN");
        assertThat((String) method.invoke(service, "English")).isEqualTo("EN");
        assertThat((String) method.invoke(service, "Spanish")).isEqualTo("ES");
    }

    @Test
    void normalizesImportedPrintingToAppValues() throws Exception {
        Method method = GoogleSheetsService.class.getDeclaredMethod("normalizePrinting", String.class);
        method.setAccessible(true);

        assertThat((String) method.invoke(service, "Foil")).isEqualTo("foil");
        assertThat((String) method.invoke(service, "Normal")).isEqualTo("nonfoil");
        assertThat((String) method.invoke(service, "No foil")).isEqualTo("nonfoil");
    }
}
