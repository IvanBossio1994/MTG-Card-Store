package com.tcg.bot.controller;

import com.tcg.bot.dto.CardKingdomProduct;
import com.tcg.bot.model.CardReservation;
import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.ReservationConditionStock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
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
    void pickupAlertShowsFlexibleEditionAndConditionRequest() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("pickupAlerts", List.class);
        method.setAccessible(true);

        CardReservation reservation = new CardReservation();
        reservation.setStatus(CardReservation.STATUS_WANTED);
        reservation.setName("Sol Ring");
        reservation.setQuantity("1");
        reservation.setClient("Sofi");
        reservation.setPhone("111");
        reservation.setDni("222");
        reservation.setPickupDate("2026-06-19");
        reservation.setNotes("[Cualquier edicion/condicion]");

        List<DashboardController.PickupAlertView> alerts =
                (List<DashboardController.PickupAlertView>) method.invoke(controller, List.of(reservation));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).cardSummary()).isEqualTo("Sol Ring (cualquier edicion/condicion)");
    }

    @Test
    void searchResultShowsParentControlsWhenSelectedConditionHasNoStock() {
        List<ReservationConditionStock> conditionStocks = List.of(
                new ReservationConditionStock("NM", 1, 1, 0, "En Stock", 10, "129.99", "214500"),
                new ReservationConditionStock("VG", 1, 1, 0, "En Stock", 11, "77.99", "129000")
        );
        DashboardController.SearchResult selectedZeroCondition = searchResult(
                "EX",
                conditionStocks,
                0,
                12
        );
        DashboardController.SearchResult selectedStockedCondition = searchResult(
                "NM",
                conditionStocks,
                1,
                10
        );

        assertThat(selectedZeroCondition.showParentStockControls()).isTrue();
        assertThat(selectedZeroCondition.displayStockQuantity()).isEqualTo(2);
        assertThat(selectedStockedCondition.showParentStockControls()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void latestUpdatesStayAlphabeticalWhenReservedCardWasAppended() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("sortedLatestUpdates", List.class);
        method.setAccessible(true);

        List<DashboardController.UpdateResult> sorted =
                (List<DashboardController.UpdateResult>) method.invoke(controller, List.of(
                        update("Vorac Battlehorns", "Mirrodin", "MRD", "271", "En Stock", 1, 2),
                        update("Zulaport Cutthroat", "Bloomburrow Commander Decks", "BLC", "0190", "En Stock", 1, 3),
                        update("Arcane Signet", "Commander 2020", "C20", "237", "Reservada", 1, 99)
                ));

        assertThat(sorted)
                .extracting(DashboardController.UpdateResult::name)
                .containsExactly("Arcane Signet", "Vorac Battlehorns", "Zulaport Cutthroat");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reservedInventoryRowsDoNotCountAsAvailableImportStock() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("indexInventoryForImport", List.class);
        method.setAccessible(true);

        Map<String, int[]> stock = (Map<String, int[]>) method.invoke(controller, List.of(
                inventoryCard("Academy Manufactor", "Bloomburrow Commander Decks", "BLC", "0264", "nonfoil", "1", "Reservada", 10),
                inventoryCard("Academy Manufactor", "Bloomburrow Commander Decks", "BLC", "0264", "nonfoil", "2", "En Stock", 11)
        ));

        int[] stockData = stock.get("academy manufactor|bloomburrow commander decks|blc|264|false");
        assertThat(stockData).containsExactly(2, 11);
    }

    @Test
    void committedReservationsReduceAvailableStockWithoutChangingTotalQuantity() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("availableInventoryQuantity", InventoryCard.class, List.class);
        method.setAccessible(true);

        InventoryCard stock = inventoryCard("Arcane Signet", "Bloomburrow Commander Decks", "BLC", "0305", "nonfoil", "5", "En Stock", 10);
        stock.setCondition("NM");

        List<CardReservation> fourReservations = reservedReservations("Arcane Signet", 4);
        List<CardReservation> fiveReservations = reservedReservations("Arcane Signet", 5);

        assertThat((int) method.invoke(controller, stock, fourReservations)).isEqualTo(1);
        assertThat((int) method.invoke(controller, stock, fiveReservations)).isZero();
        assertThat(stock.getQuantity()).isEqualTo("5");
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
    void groupedImportAlternativesShowTotalStockAndStockedOptionsFirst() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "addImportResultsForLine",
                List.class,
                DashboardController.ParsedImportLine.class,
                Map.class,
                List.class,
                Map.class
        );
        method.setAccessible(true);

        List<DashboardController.ImportResult> results = new ArrayList<>();
        DashboardController.ParsedImportLine line = new DashboardController.ParsedImportLine(
                "Sol Ring",
                1,
                "Sol Ring",
                "",
                "",
                false,
                0
        );
        CardKingdomProduct alpha = product("Sol Ring", "Alpha", "LEA-269");
        CardKingdomProduct beta = product("Sol Ring", "Beta", "LEB-270");
        CardKingdomProduct collectors = product("Sol Ring", "Collectors Ed", "CED-270");
        CardKingdomProduct commander = product("Sol Ring", "Commander", "CMD-261");
        List<CardKingdomProduct> products = List.of(alpha, beta, collectors, commander);
        Map<String, int[]> inventoryIndex = Map.of(
                "sol ring|collectors ed|ced|270|false", new int[]{3, 30},
                "sol ring|beta|leb|270|false", new int[]{2, 20},
                "sol ring|commander|cmd|261|false", new int[]{1, 40}
        );

        method.invoke(controller, results, line, Map.of("sol ring", products), products, inventoryIndex);

        assertThat(results).hasSize(1);
        DashboardController.ImportResult result = results.get(0);
        assertThat(result.status()).isEqualTo("OTRA VERSION");
        assertThat(result.stockQuantity()).isEqualTo(6);
        assertThat(result.alternatives())
                .extracting(DashboardController.ImportOption::stockQuantity)
                .containsExactly(3, 2, 1, 0);
        assertThat(result.alternatives())
                .extracting(DashboardController.ImportOption::edition)
                .containsExactly("Collectors Ed", "Beta", "Commander", "Alpha");
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

    private CardKingdomProduct product(String name, String edition, String sku) {
        CardKingdomProduct product = product(name, "");
        product.setEdition(edition);
        product.setSku(sku);
        return product;
    }

    private DashboardController.UpdateResult update(
            String name,
            String edition,
            String setCode,
            String collectorNumber,
            String action,
            int stockQuantity,
            int rowIndex
    ) {
        return new DashboardController.UpdateResult(
                name,
                edition,
                setCode,
                collectorNumber,
                "nonfoil",
                "1000",
                "0.99",
                action,
                "NM",
                List.of(),
                rowIndex,
                stockQuantity,
                false
        );
    }

    private InventoryCard inventoryCard(
            String name,
            String setName,
            String setCode,
            String collectorNumber,
            String printing,
            String quantity,
            String action,
            int rowIndex
    ) {
        InventoryCard card = new InventoryCard();
        card.setName(name);
        card.setSetName(setName);
        card.setSetCode(setCode);
        card.setCollectorNumber(collectorNumber);
        card.setPrinting(printing);
        card.setQuantity(quantity);
        card.setAction(action);
        card.setRowIndex(rowIndex);
        return card;
    }

    private List<CardReservation> reservedReservations(String name, int quantity) {
        List<CardReservation> reservations = new ArrayList<>();
        for (int index = 0; index < quantity; index++) {
            CardReservation reservation = new CardReservation();
            reservation.setStatus(CardReservation.STATUS_RESERVED);
            reservation.setName(name);
            reservation.setSetName("Bloomburrow Commander Decks");
            reservation.setSetCode("BLC");
            reservation.setCollectorNumber("0305");
            reservation.setPrinting("nonfoil");
            reservation.setCondition("NM");
            reservation.setQuantity("1");
            reservations.add(reservation);
        }
        return reservations;
    }

    private DashboardController.SearchResult searchResult(
            String selectedCondition,
            List<ReservationConditionStock> conditionStocks,
            int stockQuantity,
            int rowIndex
    ) {
        return new DashboardController.SearchResult(
                "Sol Ring",
                "Unlimited",
                "2ED-270",
                "2ED",
                "270",
                "-",
                "No Foil",
                selectedCondition,
                "129.99",
                "103.99",
                "77.99",
                "",
                "214500",
                "172000",
                "129000",
                "",
                1,
                stockQuantity,
                1,
                0,
                10,
                rowIndex,
                11,
                0,
                conditionStocks,
                "172000",
                stockQuantity,
                rowIndex
        );
    }
}
