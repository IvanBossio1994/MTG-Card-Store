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
    @SuppressWarnings("unchecked")
    void pickupAlertKeepsFlexibleIntentEvenWhenReservedRequestWasAssigned() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("pickupAlerts", List.class);
        method.setAccessible(true);

        CardReservation reservation = new CardReservation();
        reservation.setStatus(CardReservation.STATUS_RESERVED);
        reservation.setName("Arcane Signet");
        reservation.setSetName("Commander Legends");
        reservation.setSetCode("CMR");
        reservation.setCollectorNumber("334");
        reservation.setPrinting("Foil");
        reservation.setCondition("NM");
        reservation.setQuantity("1");
        reservation.setClient("Sofi");
        reservation.setPhone("111");
        reservation.setDni("222");
        reservation.setPickupDate("2026-06-19");
        reservation.setNotes("[Cualquier edicion/condicion]");

        List<DashboardController.PickupAlertView> alerts =
                (List<DashboardController.PickupAlertView>) method.invoke(controller, List.of(reservation));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).cardSummary()).isEqualTo("Arcane Signet (cualquier edicion/condicion)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pickupAlertShowsSetCodeAndNumberForSpecificRequest() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("pickupAlerts", List.class);
        method.setAccessible(true);

        CardReservation reservation = new CardReservation();
        reservation.setStatus(CardReservation.STATUS_RESERVED);
        reservation.setName("Arcane Signet");
        reservation.setSetName("Commander Legends");
        reservation.setSetCode("CMR");
        reservation.setCollectorNumber("334");
        reservation.setPrinting("Foil");
        reservation.setCondition("NM");
        reservation.setQuantity("1");
        reservation.setClient("Sofi");
        reservation.setPhone("111");
        reservation.setDni("222");
        reservation.setPickupDate("2026-06-19");

        List<DashboardController.PickupAlertView> alerts =
                (List<DashboardController.PickupAlertView>) method.invoke(controller, List.of(reservation));

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).cardSummary()).isEqualTo("Arcane Signet - CMR/334");
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
    void reservedInventoryRowsStillCountAsAvailableImportStockWithoutLedgerReservations() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("indexInventoryForImport", List.class);
        method.setAccessible(true);

        Map<String, int[]> stock = (Map<String, int[]>) method.invoke(controller, List.of(
                inventoryCard("Academy Manufactor", "Bloomburrow Commander Decks", "BLC", "0264", "nonfoil", "1", "Reservada", 10),
                inventoryCard("Academy Manufactor", "Bloomburrow Commander Decks", "BLC", "0264", "nonfoil", "2", "En Stock", 11)
        ));

        int[] stockData = stock.get("academy manufactor|bloomburrow commander decks|blc|264|false");
        assertThat(stockData).containsExactly(3, 10);
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
    void conditionStocksShowLedgerReservationsAbovePhysicalStock() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("conditionStocksForInventoryCards", List.class, List.class);
        method.setAccessible(true);

        InventoryCard stock = inventoryCard("Arcane Signet", "Bloomburrow Commander Decks", "BLC", "0305", "nonfoil", "1", "Reservada", 10);
        stock.setCondition("NM");

        List<ReservationConditionStock> conditionStocks = (List<ReservationConditionStock>) method.invoke(
                controller,
                List.of(stock),
                reservedReservations("Arcane Signet", 2)
        );

        assertThat(conditionStocks).hasSize(1);
        assertThat(conditionStocks.get(0).quantity()).isEqualTo(1);
        assertThat(conditionStocks.get(0).reservedQuantity()).isEqualTo(2);
        assertThat(conditionStocks.get(0).availableQuantity()).isZero();

        DashboardController.UpdateResult updateResult = new DashboardController.UpdateResult(
                "Arcane Signet",
                "Bloomburrow Commander Decks",
                "BLC",
                "0305",
                "nonfoil",
                "4100",
                "2.49",
                "Reservada",
                "NM",
                conditionStocks,
                10,
                1,
                false
        );

        assertThat(updateResult.displayStockBreakdown()).isEqualTo("1 total | 2 reservadas | 0 disponibles");
        assertThat(updateResult.displayAction()).isEqualTo("Reservada");
    }

    @Test
    void familySnapshotAggregatesConditionsAndAssignsFlexibleReservationsOnce() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("stockSnapshot", InventoryCard.class, List.class, List.class);
        method.setAccessible(true);

        InventoryCard nm = inventoryCard("Arcane Signet", "Commander Legends", "CMR", "334", "nonfoil", "2", "En Stock", 10);
        nm.setCondition("NM");
        InventoryCard ex = inventoryCard("Arcane Signet", "Commander Legends", "CMR", "334", "nonfoil", "1", "En Stock", 11);
        ex.setCondition("EX");

        CardReservation flexible = reservedReservation("Arcane Signet", "Commander Legends", "CMR", "334", "", "[Cualquier edicion/condicion]");
        CardReservation exReservation = reservedReservation("Arcane Signet", "Commander Legends", "CMR", "334", "EX", "");

        DashboardController.StockSnapshot snapshot = (DashboardController.StockSnapshot) method.invoke(
                controller,
                nm,
                List.of(nm, ex),
                List.of(flexible, exReservation)
        );

        assertThat(snapshot.stockTotal()).isEqualTo(3);
        assertThat(snapshot.reservedQuantity()).isEqualTo(2);
        assertThat(snapshot.availableQuantity()).isEqualTo(1);
        assertThat(snapshot.summary()).isEqualTo("3 total | 2 reservadas | 1 disponibles");
        assertThat(snapshot.conditionStocks()).extracting(ReservationConditionStock::reservedQuantity)
                .containsExactly(1, 1);
    }

    @Test
    void reservationStockSelectionUsesReservedConditionStocksForFlexibleDelivery() throws Exception {
        InventoryCard nm = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 10);
        nm.setCondition("NM");
        InventoryCard g = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 11);
        g.setCondition("G");

        List<CardReservation> reservations = List.of(
                reservedReservation("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "", "[Cualquier edicion/condicion]"),
                reservedReservation("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "", "[Cualquier edicion/condicion]")
        );

        Object selection = reservationStockSelection(reservations.get(0), List.of(nm, g), reservations, true, 1);
        InventoryCard selected = selectedReservationCard(selection);

        assertThat(selected).isNotNull();
        assertThat(selected.getRowIndex()).isIn(10, 11);
        assertThat(selectedReservationQuantity(selection)).isEqualTo(1);
    }

    @Test
    void reservationStockSelectionUsesAvailableConditionStocksForFlexibleReassignment() throws Exception {
        InventoryCard nm = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 10);
        nm.setCondition("NM");
        InventoryCard g = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 11);
        g.setCondition("G");

        CardReservation alreadyReserved = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "",
                "[Cualquier edicion/condicion]"
        );
        CardReservation wanted = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "",
                "[Cualquier edicion/condicion]"
        );
        wanted.setStatus(CardReservation.STATUS_WANTED);

        Object selection = reservationStockSelection(wanted, List.of(nm, g), List.of(alreadyReserved, wanted), false, 1);
        InventoryCard selected = selectedReservationCard(selection);

        assertThat(selected).isNotNull();
        assertThat(selected.getRowIndex()).isEqualTo(11);
        assertThat(selectedReservationQuantity(selection)).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void assignedFlexibleReservationDoesNotCountInOtherEditionFamily() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("conditionStocksForInventoryCards", List.class, List.class);
        method.setAccessible(true);

        InventoryCard commanderLegends = inventoryCard("Arcane Signet", "Commander Legends", "CMR", "334", "nonfoil", "2", "En Stock", 10);
        commanderLegends.setCondition("NM");
        CardReservation assignedElsewhere = reservedReservation(
                "Arcane Signet",
                "Bloomburrow Commander Decks",
                "BLC",
                "0305",
                "NM",
                "[Cualquier edicion/condicion]"
        );

        List<ReservationConditionStock> conditionStocks = (List<ReservationConditionStock>) method.invoke(
                controller,
                List.of(commanderLegends),
                List.of(assignedElsewhere)
        );

        assertThat(conditionStocks).isEmpty();
    }

    @Test
    void singleConditionReservationKeepsAvailabilityWithoutShowingDropdown() {
        List<ReservationConditionStock> conditionStocks = List.of(
                new ReservationConditionStock("NM", 5, 1, 4, "En Stock", 10, "2.49", "4100")
        );

        DashboardController.SearchResult searchResult = searchResult("NM", conditionStocks, 5, 10);
        DashboardController.UpdateResult updateResult = new DashboardController.UpdateResult(
                "Arcane Signet",
                "Commander 2020",
                "C20",
                "237",
                "nonfoil",
                "4100",
                "2.49",
                "En Stock",
                "NM",
                conditionStocks,
                10,
                5,
                false
        );

        assertThat(searchResult.showConditionStockOptions()).isFalse();
        assertThat(searchResult.displayAvailableQuantity()).isEqualTo(1);
        assertThat(searchResult.displayStockBreakdown()).isEqualTo("5 total | 4 reservadas | 1 disponibles");
        assertThat(searchResult.availableStockQuantityForCondition("NM")).isEqualTo(1);
        assertThat(updateResult.showConditionStockOptions()).isFalse();
        assertThat(updateResult.displayAvailableQuantity()).isEqualTo(1);
        assertThat(updateResult.displayStockBreakdown()).isEqualTo("5 total | 4 reservadas | 1 disponibles");
    }

    @Test
    void reservedActionWithoutLedgerReservationsDoesNotReduceAvailableStockInProductFamily() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("availableInventoryQuantity", List.class, List.class);
        method.setAccessible(true);

        InventoryCard reserved = inventoryCard("Arcane Signet", "Commander 2020", "C20", "237", "nonfoil", "2", "Reservada", 10);
        reserved.setCondition("NM");
        InventoryCard available = inventoryCard("Arcane Signet", "Commander 2020", "C20", "237", "nonfoil", "3", "En Stock", 11);
        available.setCondition("EX");
        InventoryCard unrelated = inventoryCard("Sol Ring", "Commander 2020", "C20", "238", "nonfoil", "7", "Reservada", 12);
        unrelated.setCondition("NM");

        assertThat((int) method.invoke(controller, List.of(reserved, available), List.of())).isEqualTo(5);

        Method cardMethod = DashboardController.class.getDeclaredMethod("availableInventoryQuantity", InventoryCard.class, List.class, List.class);
        cardMethod.setAccessible(true);
        assertThat((int) cardMethod.invoke(controller, available, List.of(), List.of(reserved, available, unrelated))).isEqualTo(3);
    }

    @Test
    void reservedActionWithoutConditionStocksDisplaysPhysicalStockOnly() {
        DashboardController.UpdateResult updateResult = update(
                "Arcane Signet",
                "Throne of Eldraine",
                "ELD",
                "331",
                "Reservada",
                2,
                15
        );

        assertThat(updateResult.displayReservedQuantity()).isZero();
        assertThat(updateResult.displayAvailableQuantity()).isEqualTo(2);
        assertThat(updateResult.displayStockBreakdown()).isEmpty();
        assertThat(updateResult.displayAction()).isEqualTo("En Stock");
    }

    @Test
    @SuppressWarnings("unchecked")
    void groupsSearchResultsByProductAndKeepsFamiliesAsRows() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("groupedSearchResults", List.class, List.class);
        method.setAccessible(true);

        List<DashboardController.SearchResult> results = List.of(
                searchFamily("Arcane Signet", "Bloomburrow Commander Decks", "BLC", "0127", 3, 0, 1, 0),
                searchFamily("Arcane Signet", "Commander Legends", "CMR", "297", 1, 0, 2, 0),
                searchFamily("Arcane Signet", "Commander Fest", "FCMR", "297", 2, 1, 3, 4),
                searchFamily("Arcane Signet", "Throne of Eldraine", "ELD", "331", 3, 0, 5, 0)
        );
        List<CardReservation> reservations = List.of(
                reservedReservation("Arcane Signet", "Bloomburrow Commander Decks", "BLC", "0127", "NM", ""),
                reservedReservation("Arcane Signet", "Commander Legends", "CMR", "297", "NM", ""),
                reservedReservation("Arcane Signet", "Commander Fest", "FCMR", "297", "NM", ""),
                reservedReservation("Arcane Signet", "Commander Fest", "FCMR", "297", "EX", ""),
                reservedReservation("Arcane Signet", "Throne of Eldraine", "ELD", "331", "NM", "")
        );

        List<DashboardController.SearchProductGroup> groups =
                (List<DashboardController.SearchProductGroup>) method.invoke(controller, results, reservations);

        assertThat(groups).hasSize(1);
        DashboardController.SearchProductGroup group = groups.get(0);
        assertThat(group.name()).isEqualTo("Arcane Signet");
        assertThat(group.stockQuantity()).isEqualTo(10);
        assertThat(group.reservedQuantity()).isEqualTo(5);
        assertThat(group.availableQuantity()).isEqualTo(5);
        assertThat(group.families())
                .extracting(DashboardController.SearchResult::familyLabel)
                .containsExactly("BLC/0127", "FCMR/297", "ELD/331", "CMR/297");
        assertThat(group.families().get(1).showConditionStockOptions()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void productAggregationDoesNotDuplicateFlexibleReservationsAcrossFamilies() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("groupedSearchResults", List.class, List.class);
        method.setAccessible(true);

        List<DashboardController.SearchResult> results = List.of(
                searchFamily("Arcane Signet", "Bloomburrow Commander Decks", "BLC", "0127", 3, 0, 1, 0),
                searchFamily("Arcane Signet", "Commander Legends", "CMR", "297", 3, 0, 2, 0)
        );
        CardReservation flexible = reservedReservation(
                "Arcane Signet",
                "",
                "",
                "",
                "NM",
                "[Cualquier edicion/condicion]"
        );

        List<DashboardController.SearchProductGroup> groups =
                (List<DashboardController.SearchProductGroup>) method.invoke(controller, results, List.of(flexible));

        assertThat(groups).hasSize(1);
        DashboardController.SearchProductGroup group = groups.get(0);
        assertThat(group.stockQuantity()).isEqualTo(6);
        assertThat(group.reservedQuantity()).isEqualTo(1);
        assertThat(group.availableQuantity()).isEqualTo(5);
        assertThat(group.families().stream()
                .mapToInt(DashboardController.SearchResult::displayReservedQuantity)
                .sum()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchProductAggregationKeepsPrecomputedConditionStocks() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("groupedSearchResults", List.class, List.class);
        method.setAccessible(true);

        List<ReservationConditionStock> conditionStocks = List.of(
                new ReservationConditionStock("NM", 1, 0, 1, "Reservada", 10, "8.49", "14500"),
                new ReservationConditionStock("G", 1, 0, 1, "Reservada", 11, "4.25", "7500")
        );
        DashboardController.SearchResult result = searchResult("NM", conditionStocks, 1, 10);

        List<DashboardController.SearchProductGroup> groups =
                (List<DashboardController.SearchProductGroup>) method.invoke(controller, List.of(result), List.of());

        assertThat(groups).hasSize(1);
        DashboardController.SearchProductGroup group = groups.get(0);
        assertThat(group.stockQuantity()).isEqualTo(2);
        assertThat(group.reservedQuantity()).isEqualTo(2);
        assertThat(group.availableQuantity()).isZero();
        assertThat(group.families().get(0).conditionStocks()).isSameAs(conditionStocks);
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateProductAggregationKeepsPrecomputedConditionStocks() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("groupedUpdateResults", List.class, List.class);
        method.setAccessible(true);

        List<ReservationConditionStock> conditionStocks = List.of(
                new ReservationConditionStock("NM", 1, 0, 1, "Reservada", 10, "8.49", "14500"),
                new ReservationConditionStock("G", 1, 0, 1, "Reservada", 11, "4.25", "7500")
        );
        DashboardController.UpdateResult update = new DashboardController.UpdateResult(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "14500",
                "8.49",
                "En Stock",
                "NM",
                conditionStocks,
                10,
                1,
                false
        );

        List<DashboardController.UpdateProductGroup> groups =
                (List<DashboardController.UpdateProductGroup>) method.invoke(controller, List.of(update), List.of());

        assertThat(groups).hasSize(1);
        DashboardController.UpdateProductGroup group = groups.get(0);
        assertThat(group.stockQuantity()).isEqualTo(2);
        assertThat(group.reservedQuantity()).isEqualTo(2);
        assertThat(group.availableQuantity()).isZero();
        assertThat(group.families().get(0).conditionStocks()).isSameAs(conditionStocks);
    }

    @Test
    void reservationsUseConditionStockBreakdownForFlexibleReservations() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "decorateReservationsWithStock",
                List.class,
                List.class,
                List.class
        );
        method.setAccessible(true);

        InventoryCard nm = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 10);
        nm.setCondition("NM");
        InventoryCard g = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 11);
        g.setCondition("G");

        CardReservation first = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "",
                "[Cualquier edicion/condicion]"
        );
        CardReservation second = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "",
                "[Cualquier edicion/condicion]"
        );

        method.invoke(controller, List.of(first, second), List.of(nm, g), List.of());

        assertThat(first.getConditionStocks()).hasSize(2);
        assertThat(first.getConditionStocks())
                .extracting(ReservationConditionStock::reservedQuantity)
                .containsExactly(1, 1);
        assertThat(first.getCurrentStock()).isEqualTo(2);
        assertThat(first.getAvailableStock()).isZero();
    }

    @Test
    void flexibleReservationMatchesAnyEditionForSharedPendingRule() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "matchesReservation",
                CardReservation.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class
        );
        method.setAccessible(true);

        CardReservation flexible = new CardReservation();
        flexible.setName("Arcane Signet");
        flexible.setSetName("Throne of Eldraine");
        flexible.setSetCode("ELD");
        flexible.setCollectorNumber("331");
        flexible.setPrinting("nonfoil");
        flexible.setNotes("[Cualquier edicion/condicion]");

        assertThat((boolean) method.invoke(
                controller,
                flexible,
                "Arcane Signet",
                "Commander 2020",
                "C20",
                "237",
                "foil"
        )).isTrue();
        assertThat((boolean) method.invoke(
                controller,
                flexible,
                "Sol Ring",
                "Commander 2020",
                "C20",
                "238",
                "nonfoil"
        )).isFalse();
    }

    @Test
    void exactReservationStillRequiresExactEditionWhenNotFlexible() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "matchesReservation",
                CardReservation.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class
        );
        method.setAccessible(true);

        CardReservation exact = new CardReservation();
        exact.setName("Arcane Signet");
        exact.setSetName("Throne of Eldraine");
        exact.setSetCode("ELD");
        exact.setCollectorNumber("331");
        exact.setPrinting("nonfoil");

        assertThat((boolean) method.invoke(
                controller,
                exact,
                "Arcane Signet",
                "Commander 2020",
                "C20",
                "237",
                "nonfoil"
        )).isFalse();
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

    private Object reservationStockSelection(
            CardReservation reservation,
            List<InventoryCard> inventoryCards,
            List<CardReservation> reservations,
            boolean reservedStock,
            int requiredQuantity
    ) throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "reservationStockSelection",
                CardReservation.class,
                List.class,
                List.class,
                boolean.class,
                int.class
        );
        method.setAccessible(true);
        return method.invoke(controller, reservation, inventoryCards, reservations, reservedStock, requiredQuantity);
    }

    private InventoryCard selectedReservationCard(Object selection) throws Exception {
        Method method = selection.getClass().getDeclaredMethod("card");
        method.setAccessible(true);
        return (InventoryCard) method.invoke(selection);
    }

    private int selectedReservationQuantity(Object selection) throws Exception {
        Method method = selection.getClass().getDeclaredMethod("availableQuantity");
        method.setAccessible(true);
        return (int) method.invoke(selection);
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

    private CardReservation reservedReservation(
            String name,
            String setName,
            String setCode,
            String collectorNumber,
            String condition,
            String notes
    ) {
        CardReservation reservation = new CardReservation();
        reservation.setStatus(CardReservation.STATUS_RESERVED);
        reservation.setName(name);
        reservation.setSetName(setName);
        reservation.setSetCode(setCode);
        reservation.setCollectorNumber(collectorNumber);
        reservation.setPrinting("nonfoil");
        reservation.setCondition(condition);
        reservation.setQuantity("1");
        reservation.setNotes(notes);
        return reservation;
    }

    private DashboardController.SearchResult searchFamily(
            String name,
            String edition,
            String setCode,
            String collectorNumber,
            int nmQuantity,
            int exQuantity,
            int nmRowIndex,
            int exRowIndex
    ) {
        String sku = setCode + "-" + collectorNumber;
        String selectedCondition = nmQuantity > 0 ? "NM" : "EX";
        int stockQuantity = nmQuantity > 0 ? nmQuantity : exQuantity;
        int rowIndex = nmQuantity > 0 ? nmRowIndex : exRowIndex;

        return new DashboardController.SearchResult(
                name,
                edition,
                sku,
                setCode,
                collectorNumber,
                "-",
                "No Foil",
                selectedCondition,
                "1.00",
                "0.80",
                "",
                "",
                "1000",
                "800",
                "",
                "",
                nmQuantity,
                exQuantity,
                0,
                0,
                nmRowIndex,
                exRowIndex,
                0,
                0,
                List.of(),
                selectedCondition.equals("NM") ? "1000" : "800",
                stockQuantity,
                rowIndex
        );
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
