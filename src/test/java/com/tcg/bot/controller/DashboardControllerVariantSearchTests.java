package com.tcg.bot.controller;

import com.tcg.bot.dto.CardKingdomProduct;
import com.tcg.bot.model.CashRegisterEntry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardControllerVariantSearchTests {

    private final DashboardController controller =
            new DashboardController(null, null, null, null, null, null);

    @Test
    void allowsReservationsAsProtectedAccessReturnPath() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "safeProtectedAccessReturnPath",
                String.class
        );
        method.setAccessible(true);

        String reservations = (String) method.invoke(controller, "/reservas");
        String reservationsQuery = (String) method.invoke(controller, "/reservas?tab=pendientes");

        assertThat(reservations).isEqualTo("/reservas");
        assertThat(reservationsQuery).isEqualTo("/reservas?tab=pendientes");
    }

    @Test
    void rejectsExternalProtectedAccessReturnPath() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "safeProtectedAccessReturnPath",
                String.class
        );
        method.setAccessible(true);

        String external = (String) method.invoke(controller, "//example.com");
        String unexpected = (String) method.invoke(controller, "/configuracion");

        assertThat(external).isEqualTo("/movimientos");
        assertThat(unexpected).isEqualTo("/movimientos");
    }

    @Test
    void keepsFlexiblePickupDateAsText() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "normalizedPickupDate",
                String.class
        );
        method.setAccessible(true);

        assertThat((String) method.invoke(controller, "A convenir")).isEqualTo("A convenir");
        assertThat((String) method.invoke(controller, "2026-06-19")).isEqualTo("2026-06-19");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportIncludesCashSalesWithoutMatchingMovement() throws Exception {
        Method reportMethod = DashboardController.class.getDeclaredMethod(
                "cashReportMonths",
                List.class,
                List.class
        );
        Method overviewMethod = DashboardController.class.getDeclaredMethod(
                "cashReportOverview",
                List.class
        );
        reportMethod.setAccessible(true);
        overviewMethod.setAccessible(true);

        CashRegisterEntry sale = new CashRegisterEntry();
        sale.setDate("2026-06-19");
        sale.setType("VENTA");
        sale.setName("Sol Ring");
        sale.setSetCode("CMM");
        sale.setCollectorNumber("410");
        sale.setPrinting("No Foil");
        sale.setQuantity("2");
        sale.setTotal("15000");

        List<?> reports = (List<?>) reportMethod.invoke(controller, List.of(sale), List.of());
        Object overview = overviewMethod.invoke(controller, reports);

        assertThat(reports).hasSize(1);
        assertThat((String) reports.get(0).getClass().getMethod("totalSales").invoke(reports.get(0))).isEqualTo("15.000");
        assertThat((int) reports.get(0).getClass().getMethod("soldQuantity").invoke(reports.get(0))).isEqualTo(2);
        assertThat((String) overview.getClass().getMethod("totalSales").invoke(overview)).isEqualTo("15.000");
        assertThat((String) overview.getClass().getMethod("bestMonth").invoke(overview)).isEqualTo("Junio 2026");
    }

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
    void parsesShortEtchedImportTokenBeforeCollector() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "parseImportLine",
                String.class
        );
        method.setAccessible(true);

        DashboardController.ParsedImportLine line = (DashboardController.ParsedImportLine) method.invoke(
                controller,
                "Mikaeus, the Unhallowed 516 *E*"
        );

        assertThat(line.name()).isEqualTo("Mikaeus, the Unhallowed");
        assertThat(line.collectorNumber()).isEqualTo("516");
        assertThat(line.foil()).isTrue();
    }

    @Test
    void ignoresTrailingMoxfieldStarBeforeCollector() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "parseImportLine",
                String.class
        );
        method.setAccessible(true);

        DashboardController.ParsedImportLine line = (DashboardController.ParsedImportLine) method.invoke(
                controller,
                "1 Gravecrawler (INR) 64\u2605"
        );

        assertThat(line.name()).isEqualTo("Gravecrawler");
        assertThat(line.setCode()).isEqualTo("INR");
        assertThat(line.collectorNumber()).isEqualTo("64");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findsEtchedImportLineByCollectorNumber() throws Exception {
        Method parseMethod = DashboardController.class.getDeclaredMethod(
                "parseImportLine",
                String.class
        );
        Method indexMethod = DashboardController.class.getDeclaredMethod(
                "indexProductsByNameOrVariation",
                List.class
        );
        Method searchMethod = DashboardController.class.getDeclaredMethod(
                "searchImportedProducts",
                Map.class,
                DashboardController.ParsedImportLine.class
        );
        parseMethod.setAccessible(true);
        indexMethod.setAccessible(true);
        searchMethod.setAccessible(true);

        CardKingdomProduct product = product("Mikaeus, the Unhallowed", "");
        product.setSku("SLC-516");
        product.setFoil("true");

        DashboardController.ParsedImportLine line = (DashboardController.ParsedImportLine) parseMethod.invoke(
                controller,
                "Mikaeus, the Unhallowed 516 *E*"
        );
        Map<String, List<CardKingdomProduct>> index =
                (Map<String, List<CardKingdomProduct>>) indexMethod.invoke(controller, List.of(product));

        List<CardKingdomProduct> matches =
                (List<CardKingdomProduct>) searchMethod.invoke(controller, index, line);

        assertThat(matches).containsExactly(product);
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
    void importsNamedPromoAliasFromVariantSegment() throws Exception {
        Method indexMethod = DashboardController.class.getDeclaredMethod(
                "indexProductsByNameOrVariation",
                List.class
        );
        Method searchMethod = DashboardController.class.getDeclaredMethod(
                "searchImportedProducts",
                java.util.Map.class,
                DashboardController.ParsedImportLine.class
        );
        indexMethod.setAccessible(true);
        searchMethod.setAccessible(true);

        CardKingdomProduct product = product(
                "Birds of Paradise",
                "Paradise Chocobo - Chocobo Bundle Foil"
        );
        product.setSku("FIC-0483");
        product.setFoil("true");
        DashboardController.ParsedImportLine line = new DashboardController.ParsedImportLine(
                "1 Paradise Chocobo (FIC) 483 F",
                1,
                "Paradise Chocobo",
                "FIC",
                "483",
                true,
                0
        );

        java.util.Map<String, List<CardKingdomProduct>> index =
                (java.util.Map<String, List<CardKingdomProduct>>) indexMethod.invoke(
                        controller,
                        List.of(product)
                );
        List<CardKingdomProduct> results = (List<CardKingdomProduct>) searchMethod.invoke(
                controller,
                index,
                line
        );

        assertThat(results).containsExactly(product);
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
    void keepsNamedAliasBeforeMetadataSegment() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "searchableVariationText",
                String.class
        );
        method.setAccessible(true);

        String candidate = (String) method.invoke(
                controller,
                "Paradise Chocobo - Chocobo Bundle Foil"
        );

        assertThat(candidate).isEqualTo("Paradise Chocobo");
    }

    @Test
    void doesNotUseEditionNameAsVariationAlias() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "searchableVariationText",
                String.class
        );
        method.setAccessible(true);

        String variantsEdition = (String) method.invoke(
                controller,
                "Modern Horizons 3 Variants"
        );
        String baseEdition = (String) method.invoke(
                controller,
                "Modern Horizons 3"
        );
        String storePromo = (String) method.invoke(
                controller,
                "Store Promo Foil"
        );
        String numberedBorderless = (String) method.invoke(
                controller,
                "0343 - Borderless"
        );
        String promoPackSet = (String) method.invoke(
                controller,
                "Promo Pack - M21"
        );

        assertThat(variantsEdition).isEmpty();
        assertThat(baseEdition).isEmpty();
        assertThat(storePromo).isEmpty();
        assertThat(numberedBorderless).isEmpty();
        assertThat(promoPackSet).isEmpty();
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
