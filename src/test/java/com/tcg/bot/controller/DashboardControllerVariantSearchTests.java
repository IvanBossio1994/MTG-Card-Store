package com.tcg.bot.controller;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardControllerVariantSearchTests {

    private final DashboardController controller =
            new DashboardController(null, null, null, null, null);

    @Test
    void parsesImportStyleSearchQuery() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "buildSearchQuery",
                String.class,
                String.class,
                String.class
        );
        method.setAccessible(true);

        String query = (String) method.invoke(
                controller,
                "1 Centurion of the Marked (PIP) 345",
                "",
                ""
        );

        assertThat(query).isEqualTo("Centurion of the Marked, set:PIP, num:345");
    }

    @Test
    @SuppressWarnings("unchecked")
    void indexesNumberedVariantByCleanVariantName() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "importVariationNameCandidates",
                String.class
        );
        method.setAccessible(true);

        List<String> candidates = (List<String>) method.invoke(
                controller,
                "0345 - Centurion of the Marked"
        );

        assertThat(candidates).contains("centurion of the marked");
    }

    @Test
    @SuppressWarnings("unchecked")
    void indexesSurgeFoilNumberedVariantByCleanVariantName() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "importVariationNameCandidates",
                String.class
        );
        method.setAccessible(true);

        List<String> candidates = (List<String>) method.invoke(
                controller,
                "0873 - Surge Foil - Centurion of the Marked"
        );

        assertThat(candidates).contains("centurion of the marked");
    }
}
