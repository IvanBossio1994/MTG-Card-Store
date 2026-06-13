package com.tcg.bot.controller;

import com.tcg.bot.dto.CardKingdomProduct;
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

    @Test
    @SuppressWarnings("unchecked")
    void doesNotIndexEditionStyleAsImportName() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "importVariationNameCandidates",
                String.class
        );
        method.setAccessible(true);

        List<String> candidates = (List<String>) method.invoke(
                controller,
                "Borderless"
        );

        assertThat(candidates).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesNotIndexPromoPrintingOrNumberAsImportName() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "importVariationNameCandidates",
                String.class
        );
        method.setAccessible(true);

        List<String> prerelease = (List<String>) method.invoke(controller, "Prerelease Foil");
        List<String> letter = (List<String>) method.invoke(controller, "A");
        List<String> number = (List<String>) method.invoke(controller, "0001");

        assertThat(prerelease).isEmpty();
        assertThat(letter).isEmpty();
        assertThat(number).isEmpty();
    }

    @Test
    void keepsBackFaceNameFromStyledVariant() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "searchableVariationText",
                String.class
        );
        method.setAccessible(true);

        String candidate = (String) method.invoke(
                controller,
                "0873 - Surge Foil - Centurion of the Marked"
        );

        assertThat(candidate).isEqualTo("Centurion of the Marked");
    }

    @Test
    void removesEventPrintingSuffixFromVariationName() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "searchableVariationText",
                String.class
        );
        method.setAccessible(true);

        String candidate = (String) method.invoke(
                controller,
                "Joshua Rosfield - CommandFest Foil"
        );

        assertThat(candidate).isEqualTo("Joshua Rosfield");
    }

    @Test
    @SuppressWarnings("unchecked")
    void filtersSearchResultsByCleanNameOrVariationOnly() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "filterProductsForNameQuery",
                List.class,
                String.class
        );
        method.setAccessible(true);

        CardKingdomProduct borderlessOnly = product("Mana Drain", "Borderless");
        CardKingdomProduct backFace = product("Fell the Profane", "Fell Mire");

        List<CardKingdomProduct> borderlessResults = (List<CardKingdomProduct>) method.invoke(
                controller,
                List.of(borderlessOnly, backFace),
                "borderless"
        );
        List<CardKingdomProduct> backFaceResults = (List<CardKingdomProduct>) method.invoke(
                controller,
                List.of(borderlessOnly, backFace),
                "fell mire"
        );

        assertThat(borderlessResults).isEmpty();
        assertThat(backFaceResults).containsExactly(backFace);
    }

    @Test
    @SuppressWarnings("unchecked")
    void setFieldCanMatchEventVariationButNameFieldCannot() throws Exception {
        Method searchMethod = DashboardController.class.getDeclaredMethod(
                "searchDashboardProducts",
                List.class,
                Class.forName("com.tcg.bot.controller.DashboardController$SearchFields")
        );
        Method filterMethod = DashboardController.class.getDeclaredMethod(
                "filterProductsForNameQuery",
                List.class,
                String.class
        );
        var fieldsConstructor = Class.forName("com.tcg.bot.controller.DashboardController$SearchFields")
                .getDeclaredConstructor(String.class, String.class, String.class);
        searchMethod.setAccessible(true);
        filterMethod.setAccessible(true);
        fieldsConstructor.setAccessible(true);

        CardKingdomProduct commandFest = product("Joshua Rosfield", "Joshua Rosfield - CommandFest Foil");
        commandFest.setSku("PFIN-001");
        commandFest.setEdition("Promotional");

        List<CardKingdomProduct> nameResults = (List<CardKingdomProduct>) filterMethod.invoke(
                controller,
                List.of(commandFest),
                "commandfest"
        );
        List<CardKingdomProduct> setResults = (List<CardKingdomProduct>) searchMethod.invoke(
                controller,
                List.of(commandFest),
                fieldsConstructor.newInstance("", "Commander Fest", "")
        );

        assertThat(nameResults).isEmpty();
        assertThat(setResults).containsExactly(commandFest);
    }

    @Test
    void groupsMultipleImportPrintingsAsOtherVersions() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "shouldGroupImportAlternatives",
                DashboardController.ParsedImportLine.class,
                List.class
        );
        method.setAccessible(true);

        DashboardController.ParsedImportLine solRing = new DashboardController.ParsedImportLine(
                "Sol Ring",
                1,
                "Sol Ring",
                "",
                "",
                false,
                0
        );
        DashboardController.ParsedImportLine islandWithSet = new DashboardController.ParsedImportLine(
                "Island (SLD) 123",
                1,
                "Island",
                "SLD",
                "123",
                false,
                0
        );

        boolean groupedSolRing = (boolean) method.invoke(
                controller,
                solRing,
                List.of(product("Sol Ring", ""), product("Sol Ring", "Judge Foil"))
        );
        boolean groupedIslandWithSet = (boolean) method.invoke(
                controller,
                islandWithSet,
                List.of(product("Island", ""), product("Island", "Borderless"))
        );

        assertThat(groupedSolRing).isTrue();
        assertThat(groupedIslandWithSet).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void possibleVariantFallbackOnlyUsesExactCleanNames() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "findPossibleVariantProducts",
                List.class,
                DashboardController.ParsedImportLine.class
        );
        method.setAccessible(true);

        CardKingdomProduct centurion = product("Lord of the Undead", "0345 - Centurion of the Marked");
        centurion.setSku("PIP-0345");
        CardKingdomProduct land = product("Wastes", "0345");
        land.setSku("PIP-0345");
        DashboardController.ParsedImportLine line = new DashboardController.ParsedImportLine(
                "1 Centurion of the Marked (PIP) 345",
                1,
                "Centurion of the Marked",
                "PIP",
                "345",
                false,
                0
        );

        List<CardKingdomProduct> results = (List<CardKingdomProduct>) method.invoke(
                controller,
                List.of(centurion, land),
                line
        );

        assertThat(results).containsExactly(centurion);
    }

    private CardKingdomProduct product(String name, String variation) {
        CardKingdomProduct product = new CardKingdomProduct();
        product.setName(name);
        product.setVariation(variation);
        product.setEdition("");
        product.setSku("");
        product.setFoil("false");
        return product;
    }
}
