package com.tcg.bot.controller;

import com.tcg.bot.dto.CardKingdomProduct;
import com.tcg.bot.dto.CardKingdomPriceListResponse;
import com.tcg.bot.model.CardReservation;
import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.ReservationClient;
import com.tcg.bot.model.ReservationConditionStock;
import com.tcg.bot.service.CardKingdomApiService;
import com.tcg.bot.service.InventoryService;
import com.tcg.bot.service.PriceComparisonService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void reservationClientsKeepSimilarNamesDistinct() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();
        when(inventoryService.getReservationClients()).thenReturn(List.of(
                reservationClient("Soky", "111", "222"),
                reservationClient("Soky dos", "333", "444")
        ));
        when(inventoryService.getReservations()).thenReturn(List.of());

        ResponseEntity<List<DashboardController.ReservationClientView>> response =
                controller.reservationClients(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(DashboardController.ReservationClientView::client)
                .containsExactly("Soky", "Soky dos");
        assertThat(response.getBody())
                .extracting(DashboardController.ReservationClientView::phone)
                .containsExactly("111", "333");
    }

    @Test
    void reservationClientsKeepSameNameWithDifferentContactDistinct() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();
        when(inventoryService.getReservationClients()).thenReturn(List.of(
                reservationClient("Soky", "111", "222"),
                reservationClient("Soky", "333", "444")
        ));
        when(inventoryService.getReservations()).thenReturn(List.of());

        ResponseEntity<List<DashboardController.ReservationClientView>> response =
                controller.reservationClients(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(DashboardController.ReservationClientView::client)
                .containsExactly("Soky", "Soky");
        assertThat(response.getBody())
                .extracting(DashboardController.ReservationClientView::phone)
                .containsExactly("111", "333");
        assertThat(response.getBody())
                .extracting(DashboardController.ReservationClientView::dni)
                .containsExactly("222", "444");
    }

    @Test
    void reservationClientsTreatSameNameAndDniAsSameIdentity() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();
        when(inventoryService.getReservationClients()).thenReturn(List.of(
                reservationClient("Soky", "111", "222"),
                reservationClient("Soky", "333", "222")
        ));
        when(inventoryService.getReservations()).thenReturn(List.of());

        ResponseEntity<List<DashboardController.ReservationClientView>> response =
                controller.reservationClients(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).client()).isEqualTo("Soky");
        assertThat(response.getBody().get(0).phone()).isEqualTo("111");
        assertThat(response.getBody().get(0).dni()).isEqualTo("222");
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
    void searchResultSelectsStockedConditionWhenEarlierSheetConditionIsEmpty() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "createSearchResult",
                CardKingdomProduct.class,
                List.class
        );
        method.setAccessible(true);
        PriceComparisonService priceComparisonService = mock(PriceComparisonService.class);
        when(priceComparisonService.calculateLocalPrice(anyDouble())).thenReturn(1000.0);
        DashboardController controllerWithPricing =
                new DashboardController(null, null, null, priceComparisonService, null, null);

        CardKingdomProduct product = product("Academy Manufactor", "Bloomburrow Commander Decks", "BLC-0264");
        CardKingdomProduct.ConditionValues prices = new CardKingdomProduct.ConditionValues();
        prices.setNmPrice("8.49");
        prices.setExPrice("6.79");
        prices.setVgPrice("5.94");
        prices.setGPrice("4.25");
        product.setConditionValues(prices);

        InventoryCard nm = inventoryCard(
                "Academy Manufactor",
                "Bloomburrow Commander Decks",
                "BLC",
                "0264",
                "nonfoil",
                "0",
                "Sin Stock",
                3
        );
        nm.setCondition("NM");
        InventoryCard vg = inventoryCard(
                "Academy Manufactor",
                "Bloomburrow Commander Decks",
                "BLC",
                "0264",
                "nonfoil",
                "0",
                "Sin Stock",
                4
        );
        vg.setCondition("VG");
        InventoryCard g = inventoryCard(
                "Academy Manufactor",
                "Bloomburrow Commander Decks",
                "BLC",
                "0264",
                "nonfoil",
                "1",
                "En Stock",
                5
        );
        g.setCondition("G");

        DashboardController.SearchResult result = (DashboardController.SearchResult) method.invoke(
                controllerWithPricing,
                product,
                List.of(nm, vg, g)
        );

        assertThat(result.selectedCondition()).isEqualTo("G");
        assertThat(result.rowIndex()).isEqualTo(5);
        assertThat(result.stockQuantity()).isEqualTo(1);
        assertThat(result.displayAvailableQuantity()).isEqualTo(1);
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
    @SuppressWarnings("unchecked")
    void ledgerAssignsFlexibleReservationOnceAcrossMultipleEditions() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("conditionStocksForInventoryCards", List.class, List.class);
        method.setAccessible(true);

        InventoryCard moc = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 10);
        moc.setCondition("NM");
        InventoryCard blc = inventoryCard("Academy Manufactor", "Bloomburrow Commander Decks", "BLC", "0264", "nonfoil", "1", "En Stock", 11);
        blc.setCondition("NM");
        CardReservation flexible = reservedReservation(
                "Academy Manufactor",
                "",
                "",
                "",
                "",
                "[Cualquier edicion/condicion]"
        );

        List<ReservationConditionStock> stocks = (List<ReservationConditionStock>) method.invoke(
                controller,
                List.of(moc, blc),
                List.of(flexible)
        );

        assertThat(stocks).hasSize(1);
        assertThat(stocks.get(0).quantity()).isEqualTo(2);
        assertThat(stocks.get(0).reservedQuantity()).isEqualTo(1);
        assertThat(stocks.get(0).availableQuantity()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void ledgerAssignsExactConditionReservationToMatchingCondition() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("conditionStocksForInventoryCards", List.class, List.class);
        method.setAccessible(true);

        InventoryCard nm = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 10);
        nm.setCondition("NM");
        InventoryCard vg = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 11);
        vg.setCondition("VG");
        CardReservation vgReservation = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "VG",
                ""
        );

        List<ReservationConditionStock> stocks = (List<ReservationConditionStock>) method.invoke(
                controller,
                List.of(nm, vg),
                List.of(vgReservation)
        );

        assertThat(stocks).extracting(ReservationConditionStock::condition).containsExactly("NM", "VG");
        ReservationConditionStock vgStock = stocks.stream()
                .filter(stock -> stock.condition().equals("VG"))
                .findFirst()
                .orElseThrow();
        assertThat(vgStock.reservedQuantity()).isEqualTo(1);
        assertThat(vgStock.availableQuantity()).isZero();
        assertThat(vgStock.action()).isEqualTo("Reservada");
    }

    @Test
    @SuppressWarnings("unchecked")
    void stockSnapshotMatchesConditionStocksForInventoryCards() throws Exception {
        Method stocksMethod = DashboardController.class.getDeclaredMethod("conditionStocksForInventoryCards", List.class, List.class);
        stocksMethod.setAccessible(true);
        Method snapshotMethod = DashboardController.class.getDeclaredMethod("stockSnapshot", InventoryCard.class, List.class, List.class);
        snapshotMethod.setAccessible(true);

        InventoryCard nm = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 10);
        nm.setCondition("NM");
        InventoryCard g = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 11);
        g.setCondition("G");
        List<CardReservation> reservations = List.of(
                reservedReservation("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "", "[Cualquier edicion/condicion]"),
                reservedReservation("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "", "[Cualquier edicion/condicion]")
        );

        List<ReservationConditionStock> stocks = (List<ReservationConditionStock>) stocksMethod.invoke(
                controller,
                List.of(nm, g),
                reservations
        );
        DashboardController.StockSnapshot snapshot = (DashboardController.StockSnapshot) snapshotMethod.invoke(
                controller,
                nm,
                List.of(nm, g),
                reservations
        );

        assertThat(snapshot.stockTotal()).isEqualTo(stocks.stream().mapToInt(ReservationConditionStock::quantity).sum());
        assertThat(snapshot.reservedQuantity()).isEqualTo(stocks.stream().mapToInt(ReservationConditionStock::reservedQuantity).sum());
        assertThat(snapshot.availableQuantity()).isEqualTo(stocks.stream().mapToInt(ReservationConditionStock::availableQuantity).sum());
        assertThat(snapshot.conditionStocks()).isEqualTo(stocks);
    }

    @Test
    void stockSnapshotActionIsReservedWhenReservedQuantityIsPartial() throws Exception {
        Method snapshotMethod = DashboardController.class.getDeclaredMethod("stockSnapshot", InventoryCard.class, List.class, List.class);
        snapshotMethod.setAccessible(true);

        InventoryCard stock = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "3", "En Stock", 10);
        stock.setCondition("NM");
        CardReservation reservation = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "NM",
                ""
        );

        DashboardController.StockSnapshot snapshot = (DashboardController.StockSnapshot) snapshotMethod.invoke(
                controller,
                stock,
                List.of(stock),
                List.of(reservation)
        );

        assertThat(snapshot.stockTotal()).isEqualTo(3);
        assertThat(snapshot.reservedQuantity()).isEqualTo(1);
        assertThat(snapshot.availableQuantity()).isEqualTo(2);
        assertThat(snapshot.action()).isEqualTo("Reservada");
        assertThat(snapshot.conditionStocks().get(0).action()).isEqualTo("Reservada");
    }

    @Test
    void stockSnapshotActionIsInStockWhenAvailableAndUnreserved() throws Exception {
        Method snapshotMethod = DashboardController.class.getDeclaredMethod("stockSnapshot", InventoryCard.class, List.class, List.class);
        snapshotMethod.setAccessible(true);

        InventoryCard stock = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "3", "En Stock", 10);
        stock.setCondition("NM");

        DashboardController.StockSnapshot snapshot = (DashboardController.StockSnapshot) snapshotMethod.invoke(
                controller,
                stock,
                List.of(stock),
                List.of()
        );

        assertThat(snapshot.stockTotal()).isEqualTo(3);
        assertThat(snapshot.reservedQuantity()).isZero();
        assertThat(snapshot.availableQuantity()).isEqualTo(3);
        assertThat(snapshot.action()).isEqualTo("En Stock");
    }

    @Test
    void stockSnapshotActionIsOutOfStockWhenNoAvailableAndNoReserved() throws Exception {
        Method snapshotMethod = DashboardController.class.getDeclaredMethod("stockSnapshot", InventoryCard.class, List.class, List.class);
        snapshotMethod.setAccessible(true);

        InventoryCard stock = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "0", "Sin Stock", 10);
        stock.setCondition("NM");

        DashboardController.StockSnapshot snapshot = (DashboardController.StockSnapshot) snapshotMethod.invoke(
                controller,
                stock,
                List.of(stock),
                List.of()
        );

        assertThat(snapshot.stockTotal()).isZero();
        assertThat(snapshot.reservedQuantity()).isZero();
        assertThat(snapshot.availableQuantity()).isZero();
        assertThat(snapshot.action()).isEqualTo("Sin Stock");
    }

    @Test
    void stockSnapshotActionIsReservedWhenFullyReserved() throws Exception {
        Method snapshotMethod = DashboardController.class.getDeclaredMethod("stockSnapshot", InventoryCard.class, List.class, List.class);
        snapshotMethod.setAccessible(true);

        InventoryCard stock = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 10);
        stock.setCondition("NM");
        CardReservation reservation = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "NM",
                ""
        );

        DashboardController.StockSnapshot snapshot = (DashboardController.StockSnapshot) snapshotMethod.invoke(
                controller,
                stock,
                List.of(stock),
                List.of(reservation)
        );

        assertThat(snapshot.stockTotal()).isEqualTo(1);
        assertThat(snapshot.reservedQuantity()).isEqualTo(1);
        assertThat(snapshot.availableQuantity()).isZero();
        assertThat(snapshot.action()).isEqualTo("Reservada");
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
    @SuppressWarnings("unchecked")
    void reservationStockSelectionChoosesSameRowAsLedgerAvailableStock() throws Exception {
        Method stocksMethod = DashboardController.class.getDeclaredMethod("conditionStocksForInventoryCards", List.class, List.class, boolean.class);
        stocksMethod.setAccessible(true);

        InventoryCard nm = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 10);
        nm.setCondition("NM");
        InventoryCard g = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 11);
        g.setCondition("G");
        CardReservation alreadyReserved = reservedReservation("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "", "[Cualquier edicion/condicion]");
        CardReservation wanted = reservedReservation("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "", "[Cualquier edicion/condicion]");
        wanted.setStatus(CardReservation.STATUS_WANTED);
        List<CardReservation> reservations = List.of(alreadyReserved, wanted);

        List<ReservationConditionStock> stocks = (List<ReservationConditionStock>) stocksMethod.invoke(
                controller,
                List.of(nm, g),
                reservations,
                true
        );
        Object selection = reservationStockSelection(wanted, List.of(nm, g), reservations, false, 1);
        InventoryCard selected = selectedReservationCard(selection);
        ReservationConditionStock ledgerAvailableStock = stocks.stream()
                .filter(stock -> stock.availableQuantity() >= 1)
                .findFirst()
                .orElseThrow();

        assertThat(selected).isNotNull();
        assertThat(selected.getRowIndex()).isEqualTo(ledgerAvailableStock.rowIndex());
        assertThat(selected.getCondition()).isEqualTo(ledgerAvailableStock.condition());
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
        assertThat(selected.getRowIndex()).isEqualTo(10);
        assertThat(selectedReservationQuantity(selection)).isEqualTo(1);
    }

    @Test
    void reservePendingReservationUsesStockSelectionInsteadOfRequestedRowOnly() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("movementsAccessUnlocked")).thenReturn(Boolean.TRUE);

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
        alreadyReserved.setId("already-reserved");
        CardReservation wanted = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "",
                "[Cualquier edicion/condicion]"
        );
        wanted.setId("wanted");
        wanted.setStatus(CardReservation.STATUS_WANTED);
        wanted.setClient("Sofi");

        when(inventoryService.getReservations()).thenReturn(List.of(alreadyReserved, wanted));
        when(inventoryService.getInventoryCards()).thenReturn(List.of(nm, g));

        ResponseEntity<?> response = controller.reservePendingReservation("wanted", "", "NM", 10, -1, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardController.ReservationStockResponse body =
                (DashboardController.ReservationStockResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.rowIndex()).isEqualTo(10);
        verify(inventoryService).updateStockState(eq(10), same(nm));
        verify(inventoryService, never()).updateStockState(eq(11), any());
        verify(inventoryService).updateReservationInventoryMatch(
                eq("wanted"),
                eq("March of the Machine Commander Decks"),
                eq("MOC"),
                eq("0346"),
                eq("nonfoil"),
                eq("NM")
        );
    }

    @Test
    void reservePendingReservationUsesPostAddQuantityForSoldReservedUnit() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();

        InventoryCard staleSelectedRow = inventoryCard(
                "Academy Manufactor",
                "Bloomburrow Commander Decks",
                "BLC",
                "0264",
                "nonfoil",
                "0",
                "Sin Stock",
                212
        );
        staleSelectedRow.setCondition("EX");
        CardReservation wanted = reservedReservation(
                "Academy Manufactor",
                "Bloomburrow Commander Decks",
                "BLC",
                "0264",
                "EX",
                ""
        );
        wanted.setId("wanted");
        wanted.setStatus(CardReservation.STATUS_WANTED);
        wanted.setClient("Sofi");
        CardReservation alreadyReserved = reservedReservation(
                "Academy Manufactor",
                "Bloomburrow Commander Decks",
                "BLC",
                "0264",
                "EX",
                ""
        );
        alreadyReserved.setId("already-reserved");
        alreadyReserved.setStatus(CardReservation.STATUS_RESERVED);
        alreadyReserved.setClient("Sofi");

        when(inventoryService.getReservations()).thenReturn(List.of(alreadyReserved, wanted));
        when(inventoryService.getInventoryCards()).thenReturn(List.of(staleSelectedRow));

        ResponseEntity<?> response = controller.reservePendingReservation("wanted", "", "EX", 212, 1, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardController.ReservationStockResponse body =
                (DashboardController.ReservationStockResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.rowIndex()).isEqualTo(212);
        assertThat(body.stockQuantity()).isEqualTo(1);
        assertThat(body.reservedQuantity()).isEqualTo(1);
        assertThat(body.availableQuantity()).isZero();
        assertThat(body.action()).isEqualTo("Reservada");

        ArgumentCaptor<InventoryCard> cardCaptor = ArgumentCaptor.forClass(InventoryCard.class);
        verify(inventoryService).updateStockState(eq(212), cardCaptor.capture());
        assertThat(cardCaptor.getValue().getQuantity()).isEqualTo("1");
        assertThat(cardCaptor.getValue().getAction()).isEqualTo("Reservada");
    }

    @Test
    void reservePendingReservationUsesIncomingPlusUnitForExactNoStockPedido() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();

        InventoryCard noStockRow = inventoryCard(
                "Academy Manufactor",
                "Modern Horizons 2 Variants",
                "FMH2",
                "469",
                "foil",
                "0",
                "Sin Stock",
                469
        );
        noStockRow.setCondition("NM");
        CardReservation wanted = reservedReservation(
                "Academy Manufactor",
                "Modern Horizons 2 Variants",
                "FMH2",
                "469",
                "NM",
                ""
        );
        wanted.setId("wanted-fmh2");
        wanted.setStatus(CardReservation.STATUS_WANTED);
        wanted.setClient("Soky");
        wanted.setPrinting("foil");

        when(inventoryService.getReservations()).thenReturn(List.of(wanted));
        when(inventoryService.getInventoryCards()).thenReturn(List.of(noStockRow));

        ResponseEntity<?> response = controller.reservePendingReservation("wanted-fmh2", "", "NM", 469, 1, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardController.ReservationStockResponse body =
                (DashboardController.ReservationStockResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.rowIndex()).isEqualTo(469);
        assertThat(body.stockQuantity()).isEqualTo(1);
        assertThat(body.reservedQuantity()).isEqualTo(1);
        assertThat(body.availableQuantity()).isZero();
        assertThat(body.action()).isEqualTo("Reservada");
        assertThat(body.snapshot().pendingInfo().quantity()).isZero();

        ArgumentCaptor<InventoryCard> cardCaptor = ArgumentCaptor.forClass(InventoryCard.class);
        verify(inventoryService).updateStockState(eq(469), cardCaptor.capture());
        assertThat(cardCaptor.getValue().getQuantity()).isEqualTo("1");
        assertThat(cardCaptor.getValue().getAction()).isEqualTo("Reservada");
        verify(inventoryService).updateReservationInventoryMatch(
                eq("wanted-fmh2"),
                eq("Modern Horizons 2 Variants"),
                eq("FMH2"),
                eq("469"),
                eq("foil"),
                eq("NM")
        );
    }

    @Test
    void reservePendingReservationLeavesRemainingExactPendingInfoAfterSeparatingOneClient() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();

        InventoryCard noStockRow = inventoryCard(
                "Academy Manufactor",
                "Murders at Karlov Manor Commander Decks",
                "MKC",
                "0221",
                "nonfoil",
                "0",
                "Sin Stock",
                221
        );
        noStockRow.setCondition("NM");
        CardReservation maria = reservedReservation(
                "Academy Manufactor",
                "Murders at Karlov Manor Commander Decks",
                "MKC",
                "0221",
                "NM",
                ""
        );
        maria.setId("wanted-maria");
        maria.setStatus(CardReservation.STATUS_WANTED);
        maria.setClient("Maria");
        maria.setPrinting("nonfoil");
        CardReservation raul = reservedReservation(
                "Academy Manufactor",
                "Murders at Karlov Manor Commander Decks",
                "MKC",
                "0221",
                "NM",
                ""
        );
        raul.setId("wanted-raul");
        raul.setStatus(CardReservation.STATUS_WANTED);
        raul.setClient("Raul");
        raul.setPrinting("nonfoil");

        when(inventoryService.getReservations()).thenReturn(new ArrayList<>(List.of(maria, raul)));
        when(inventoryService.getInventoryCards()).thenReturn(List.of(noStockRow));

        ResponseEntity<?> response = controller.reservePendingReservation("wanted-maria", "", "NM", 221, 1, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardController.ReservationStockResponse body =
                (DashboardController.ReservationStockResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.stockQuantity()).isEqualTo(1);
        assertThat(body.reservedQuantity()).isEqualTo(1);
        assertThat(body.availableQuantity()).isZero();
        assertThat(body.action()).isEqualTo("Reservada");
        assertThat(body.pendingInfo().quantity()).isEqualTo(1);
        assertThat(body.pendingInfo().tooltip()).contains("Raul - MKC-0221 NM");
        assertThat(body.snapshot().pendingInfo().quantity()).isEqualTo(1);
        assertThat(body.snapshot().pendingInfo().tooltip()).contains("Raul - MKC-0221 NM");
    }

    @Test
    void reservePendingReservationLeavesRemainingFlexiblePendingOnlyAtProductLevel() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();

        InventoryCard stockRow = inventoryCard(
                "Reaper of Sheoldred",
                "March of the Machine Promos",
                "MNP",
                "072",
                "nonfoil",
                "2",
                "En Stock",
                72
        );
        stockRow.setCondition("NM");
        CardReservation maria = reservedReservation(
                "Reaper of Sheoldred",
                "",
                "",
                "",
                "",
                "[Cualquier edicion/condicion]"
        );
        maria.setId("wanted-maria");
        maria.setStatus(CardReservation.STATUS_WANTED);
        maria.setClient("Maria");
        CardReservation raul = reservedReservation(
                "Reaper of Sheoldred",
                "",
                "",
                "",
                "",
                "[Cualquier edicion/condicion]"
        );
        raul.setId("wanted-raul");
        raul.setStatus(CardReservation.STATUS_WANTED);
        raul.setClient("Raul");
        List<CardReservation> reservations = new ArrayList<>(List.of(maria, raul));

        when(inventoryService.getReservations()).thenReturn(reservations);
        when(inventoryService.getInventoryCards()).thenReturn(List.of(stockRow));

        ResponseEntity<?> response = controller.reservePendingReservation("wanted-raul", "", "NM", 72, 2, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardController.ReservationStockResponse body =
                (DashboardController.ReservationStockResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(raul.getStatus()).isEqualTo(CardReservation.STATUS_RESERVED);
        assertThat(body.pendingInfo().quantity()).isEqualTo(1);
        assertThat(body.pendingInfo().tooltip())
                .contains("Maria - cualquier edicion/condicion")
                .doesNotContain("Raul");
        assertThat(body.familyPendingInfo().quantity()).isZero();
        assertThat(body.snapshot().pendingInfo().quantity()).isZero();

        ResponseEntity<List<DashboardController.PendingReservationView>> pendingResponse =
                controller.pendingReservationsForCard(
                        "Reaper of Sheoldred",
                        "March of the Machine Promos",
                        "MNP",
                        "072",
                        "nonfoil",
                        "NM",
                        72,
                        request
                );

        assertThat(pendingResponse.getBody())
                .extracting(DashboardController.PendingReservationView::id)
                .containsExactly("wanted-maria");
    }

    @Test
    void reservePendingReservationCreatesReservedRowForExactNoStockPedidoWhenRowIndexIsMissing() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        CardKingdomApiService cardKingdomApiService = mock(CardKingdomApiService.class);
        PriceComparisonService priceComparisonService = mock(PriceComparisonService.class);
        DashboardController controller = new DashboardController(
                inventoryService,
                cardKingdomApiService,
                null,
                priceComparisonService,
                null,
                null
        );
        HttpServletRequest request = unlockedRequest();

        CardReservation wanted = reservedReservation(
                "Academy Manufactor",
                "Modern Horizons 2 Variants",
                "MH2",
                "469",
                "NM",
                ""
        );
        wanted.setId("wanted-mh2");
        wanted.setStatus(CardReservation.STATUS_WANTED);
        wanted.setClient("Raul");
        wanted.setPrinting("nonfoil");

        CardKingdomProduct product = product("Academy Manufactor", "Modern Horizons 2 Variants", "MH2-469");
        product.setFoil("false");
        CardKingdomPriceListResponse priceList = new CardKingdomPriceListResponse();
        priceList.setData(List.of(product));

        when(inventoryService.getReservations()).thenReturn(List.of(wanted));
        when(inventoryService.getInventoryCards()).thenReturn(List.of());
        when(inventoryService.appendInventoryCard(any(InventoryCard.class))).thenReturn(469);
        when(cardKingdomApiService.getPriceList()).thenReturn(priceList);
        when(priceComparisonService.getBestConditionPrice(eq(product), any(InventoryCard.class))).thenReturn(1.0);
        when(priceComparisonService.calculateLocalPrice(1.0)).thenReturn(1000.0);

        ResponseEntity<?> response = controller.reservePendingReservation("wanted-mh2", "MH2-469", "NM", 0, -1, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardController.ReservationStockResponse body =
                (DashboardController.ReservationStockResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.rowIndex()).isEqualTo(469);
        assertThat(body.stockQuantity()).isEqualTo(1);
        assertThat(body.reservedQuantity()).isEqualTo(1);
        assertThat(body.availableQuantity()).isZero();
        assertThat(body.action()).isEqualTo("Reservada");
        assertThat(body.pendingInfo().quantity()).isZero();
        assertThat(body.snapshot().stockTotal()).isEqualTo(1);
        assertThat(body.snapshot().reservedQuantity()).isEqualTo(1);
        assertThat(body.snapshot().availableQuantity()).isZero();
        assertThat(body.snapshot().pendingInfo().quantity()).isZero();

        ArgumentCaptor<InventoryCard> cardCaptor = ArgumentCaptor.forClass(InventoryCard.class);
        verify(inventoryService).appendInventoryCard(cardCaptor.capture());
        assertThat(cardCaptor.getValue().getName()).isEqualTo("Academy Manufactor");
        assertThat(cardCaptor.getValue().getSetCode()).isEqualTo("MH2");
        assertThat(cardCaptor.getValue().getCollectorNumber()).isEqualTo("469");
        assertThat(cardCaptor.getValue().getPrinting()).isEqualTo("nonfoil");
        assertThat(cardCaptor.getValue().getCondition()).isEqualTo("NM");
        assertThat(cardCaptor.getValue().getQuantity()).isEqualTo("1");
        assertThat(cardCaptor.getValue().getAction()).isEqualTo("Reservada");
        verify(inventoryService, never()).updateStockState(anyInt(), any());
        verify(inventoryService).updateReservationStatus("wanted-mh2", CardReservation.STATUS_RESERVED);
        verify(inventoryService).updateReservationInventoryMatch(
                eq("wanted-mh2"),
                eq("Modern Horizons 2 Variants"),
                eq("MH2"),
                eq("469"),
                eq("nonfoil"),
                eq("NM")
        );
    }

    @Test
    void reservePendingReservationRejectsExactNoStockPedidoWithoutIncomingStock() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();

        InventoryCard noStockRow = inventoryCard(
                "Academy Manufactor",
                "Modern Horizons 2 Variants",
                "FMH2",
                "469",
                "foil",
                "0",
                "Sin Stock",
                469
        );
        noStockRow.setCondition("NM");
        CardReservation wanted = reservedReservation(
                "Academy Manufactor",
                "Modern Horizons 2 Variants",
                "FMH2",
                "469",
                "NM",
                ""
        );
        wanted.setId("wanted-fmh2");
        wanted.setStatus(CardReservation.STATUS_WANTED);
        wanted.setClient("Soky");
        wanted.setPrinting("foil");

        when(inventoryService.getReservations()).thenReturn(List.of(wanted));
        when(inventoryService.getInventoryCards()).thenReturn(List.of(noStockRow));

        ResponseEntity<?> response = controller.reservePendingReservation("wanted-fmh2", "", "NM", 469, -1, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(DashboardController.ApiMessage.class);
        assertThat(((DashboardController.ApiMessage) response.getBody()).message())
                .contains("No hay stock disponible");
        verify(inventoryService, never()).updateStockState(anyInt(), any());
        verify(inventoryService, never()).updateReservationInventoryMatch(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void pendingReservationPopupUsesSelectedConditionCompatibility() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();

        CardReservation exactNm = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "NM",
                ""
        );
        exactNm.setId("exact-nm");
        exactNm.setStatus(CardReservation.STATUS_WANTED);
        CardReservation anyCondition = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "",
                "[Cualquier edicion/condicion]"
        );
        anyCondition.setId("any-condition");
        anyCondition.setStatus(CardReservation.STATUS_WANTED);
        when(inventoryService.getReservations()).thenReturn(List.of(exactNm, anyCondition));

        ResponseEntity<List<DashboardController.PendingReservationView>> response =
                controller.pendingReservationsForCard(
                        "Academy Manufactor",
                        "March of the Machine Commander Decks",
                        "MOC",
                        "0346",
                        "nonfoil",
                        "G",
                        0,
                        request
                );

        assertThat(response.getBody())
                .extracting(DashboardController.PendingReservationView::id)
                .containsExactly("any-condition");
    }

    @Test
    void rowLevelPendingSnapshotExcludesFlexiblePendingReservations() throws Exception {
        InventoryCard stockRow = inventoryCard(
                "Reaper of Sheoldred",
                "March of the Machine Promos",
                "MNP",
                "072",
                "nonfoil",
                "0",
                "Sin Stock",
                72
        );
        stockRow.setCondition("NM");
        CardReservation flexible = reservedReservation(
                "Reaper of Sheoldred",
                "",
                "",
                "",
                "",
                "[Cualquier edicion/condicion]"
        );
        flexible.setStatus(CardReservation.STATUS_WANTED);
        flexible.setClient("Maria");
        CardReservation exact = reservedReservation(
                "Reaper of Sheoldred",
                "March of the Machine Promos",
                "MNP",
                "072",
                "NM",
                ""
        );
        exact.setStatus(CardReservation.STATUS_WANTED);
        exact.setClient("Raul");

        DashboardController.StockSnapshot flexibleOnly =
                stockSnapshot(stockRow, List.of(stockRow), List.of(flexible));
        DashboardController.StockSnapshot exactOnly =
                stockSnapshot(stockRow, List.of(stockRow), List.of(exact));

        assertThat(flexibleOnly.pendingInfo().quantity()).isZero();
        assertThat(exactOnly.pendingInfo().quantity()).isEqualTo(1);
        assertThat(exactOnly.pendingInfo().tooltip()).contains("Raul - MNP-072 NM");
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchRowPendingQuantitiesExcludeFlexiblePendingReservations() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        DashboardController.SearchResult row = searchFamily(
                "Reaper of Sheoldred",
                "March of the Machine Promos",
                "MNP",
                "072",
                0,
                0,
                72,
                0
        );
        CardReservation flexible = reservedReservation(
                "Reaper of Sheoldred",
                "",
                "",
                "",
                "",
                "[Cualquier edicion/condicion]"
        );
        flexible.setStatus(CardReservation.STATUS_WANTED);
        flexible.setClient("Maria");
        CardReservation exact = reservedReservation(
                "Reaper of Sheoldred",
                "March of the Machine Promos",
                "MNP",
                "072",
                "NM",
                ""
        );
        exact.setStatus(CardReservation.STATUS_WANTED);
        exact.setClient("Raul");
        when(inventoryService.getReservations()).thenReturn(List.of(flexible, exact));

        Method method = DashboardController.class.getDeclaredMethod("pendingReservationQuantitiesForSearchResults", List.class);
        method.setAccessible(true);
        Map<String, DashboardController.PendingReservationInfo> quantities =
                (Map<String, DashboardController.PendingReservationInfo>) method.invoke(controller, List.of(row));

        assertThat(quantities).containsKey(row.reservationKey());
        assertThat(quantities.get(row.reservationKey()).getQuantity()).isEqualTo(1);
        assertThat(quantities.get(row.reservationKey()).getTooltip())
                .contains("Raul - MNP-072 NM")
                .doesNotContain("Maria");
    }

    @Test
    void pendingReservationPopupExcludesReservedExactConditionMatch() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();

        CardReservation reservedVg = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "VG",
                ""
        );
        reservedVg.setId("reserved-vg");
        CardReservation wantedNm = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "NM",
                ""
        );
        wantedNm.setId("wanted-nm");
        wantedNm.setStatus(CardReservation.STATUS_WANTED);
        when(inventoryService.getReservations()).thenReturn(List.of(reservedVg, wantedNm));

        ResponseEntity<List<DashboardController.PendingReservationView>> response =
                controller.pendingReservationsForCard(
                        "Academy Manufactor",
                        "March of the Machine Commander Decks",
                        "MOC",
                        "0346",
                        "nonfoil",
                        "VG",
                        0,
                        request
                );

        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void pendingReservationPopupRowIndexFallbackDoesNotReturnReservedRows() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();
        InventoryCard vgStock = inventoryCard(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "0",
                "Reservada",
                9
        );
        vgStock.setCondition("VG");
        CardReservation rowMatchedVg = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "VG",
                ""
        );
        rowMatchedVg.setId("row-matched-vg");
        CardReservation staleEx = reservedReservation(
                "Academy Manufactor",
                "Legacy Metadata",
                "OLD",
                "999",
                "EX",
                ""
        );
        staleEx.setId("stale-ex");
        CardReservation otherCard = reservedReservation(
                "Sol Ring",
                "Legacy Metadata",
                "OLD",
                "999",
                "VG",
                ""
        );
        otherCard.setId("other-card");
        when(inventoryService.getReservations()).thenReturn(List.of(staleEx, rowMatchedVg, otherCard));
        when(inventoryService.getInventoryCards()).thenReturn(List.of(vgStock));

        ResponseEntity<List<DashboardController.PendingReservationView>> response =
                controller.pendingReservationsForCard(
                        "Academy Manufactor",
                        "Wrong Metadata",
                        "WRG",
                        "999",
                        "nonfoil",
                        "VG",
                        9,
                        request
                );

        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void pendingReservationPopupRowIndexFallbackRejectsSameNameConditionDifferentProduct() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();
        InventoryCard vgStock = inventoryCard(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "0",
                "Reservada",
                9
        );
        vgStock.setCondition("VG");
        CardReservation otherProduct = reservedReservation(
                "Academy Manufactor",
                "Bloomburrow Commander Decks",
                "BLC",
                "0264",
                "VG",
                ""
        );
        otherProduct.setId("other-product");
        when(inventoryService.getReservations()).thenReturn(List.of(otherProduct));
        when(inventoryService.getInventoryCards()).thenReturn(List.of(vgStock));

        ResponseEntity<List<DashboardController.PendingReservationView>> response =
                controller.pendingReservationsForCard(
                        "Academy Manufactor",
                        "Wrong Metadata",
                        "WRG",
                        "999",
                        "nonfoil",
                        "VG",
                        9,
                        request
                );

        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void reservePendingReservationAcceptsAlreadyReservedSelection() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();
        InventoryCard vgStock = inventoryCard(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "1",
                "Reservada",
                9
        );
        vgStock.setCondition("VG");
        CardReservation reserved = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "VG",
                ""
        );
        reserved.setId("reserved-vg");
        reserved.setClient("Sofi");
        when(inventoryService.getReservations()).thenReturn(List.of(reserved));
        when(inventoryService.getInventoryCards()).thenReturn(List.of(vgStock));

        ResponseEntity<?> response = controller.reservePendingReservation("reserved-vg", "", "VG", 9, 1, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardController.ReservationStockResponse body =
                (DashboardController.ReservationStockResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.rowIndex()).isEqualTo(9);
        assertThat(body.stockQuantity()).isEqualTo(1);
        assertThat(body.reservedQuantity()).isEqualTo(1);
        assertThat(body.availableQuantity()).isZero();
        verify(inventoryService).updateStockState(eq(9), same(vgStock));
    }

    @Test
    void reservePendingReservationRejectsInactiveStatus() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();
        CardReservation inactive = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "VG",
                ""
        );
        inactive.setId("inactive");
        inactive.setStatus(CardReservation.STATUS_IN_STOCK);
        when(inventoryService.getReservations()).thenReturn(List.of(inactive));

        ResponseEntity<?> response = controller.reservePendingReservation("inactive", "", "VG", 9, 1, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(inventoryService, never()).updateStockState(anyInt(), any());
    }

    @Test
    void stockSnapshotUsesGroupedTotalMinusReservedAfterReservedConditionSale() throws Exception {
        InventoryCard ex = inventoryCard("Academy Manufactor", "Bloomburrow Commander Decks", "BLC", "0264", "nonfoil", "0", "Reservada", 6);
        ex.setCondition("EX");
        InventoryCard g = inventoryCard("Academy Manufactor", "Bloomburrow Commander Decks", "BLC", "0264", "nonfoil", "3", "En Stock", 5);
        g.setCondition("G");
        CardReservation carlito = reservedReservation(
                "Academy Manufactor",
                "Bloomburrow Commander Decks",
                "BLC",
                "0264",
                "EX",
                ""
        );
        carlito.setId("carlito-blc-ex");
        carlito.setClient("Carlito");

        DashboardController.StockSnapshot snapshot = stockSnapshot(ex, List.of(ex, g), List.of(carlito));

        assertThat(snapshot.stockTotal()).isEqualTo(3);
        assertThat(snapshot.reservedQuantity()).isEqualTo(1);
        assertThat(snapshot.availableQuantity()).isEqualTo(2);
        assertThat(snapshot.summary()).isEqualTo("3 total | 1 reservadas | 2 disponibles");
        assertThat(snapshot.conditionStocks())
                .filteredOn(stock -> stock.condition().equals("EX"))
                .singleElement()
                .satisfies(stock -> {
                    assertThat(stock.quantity()).isZero();
                    assertThat(stock.reservedQuantity()).isEqualTo(1);
                    assertThat(stock.availableQuantity()).isZero();
                    assertThat(stock.action()).isEqualTo("Reservada");
                    assertThat(stock.rowIndex()).isEqualTo(6);
                });
    }

    @Test
    void pendingReservationLookupExcludesReservationResponsibleForReservedConditionRow() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();
        InventoryCard ex = inventoryCard("Academy Manufactor", "Bloomburrow Commander Decks", "BLC", "0264", "nonfoil", "0", "Reservada", 6);
        ex.setCondition("EX");
        InventoryCard g = inventoryCard("Academy Manufactor", "Bloomburrow Commander Decks", "BLC", "0264", "nonfoil", "3", "En Stock", 5);
        g.setCondition("G");
        CardReservation carlito = reservedReservation(
                "Academy Manufactor",
                "Bloomburrow Commander Decks",
                "BLC",
                "0264",
                "EX",
                ""
        );
        carlito.setId("carlito-blc-ex");
        carlito.setClient("Carlito");
        when(inventoryService.getReservations()).thenReturn(List.of(carlito));
        when(inventoryService.getInventoryCards()).thenReturn(List.of(ex, g));

        ResponseEntity<List<DashboardController.PendingReservationView>> response =
                controller.pendingReservationsForCard(
                        "Academy Manufactor",
                        "Bloomburrow Commander Decks",
                        "BLC",
                        "0264",
                        "nonfoil",
                        "EX",
                        6,
                        request
                );

        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void reservedConditionStockDoesNotAppearAsPendingReservationLookup() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();
        InventoryCard ex = inventoryCard("Academy Manufactor", "Bloomburrow Commander Decks", "BLC", "0264", "nonfoil", "0", "Reservada", 6);
        ex.setCondition("EX");
        InventoryCard g = inventoryCard("Academy Manufactor", "Bloomburrow Commander Decks", "BLC", "0264", "nonfoil", "3", "En Stock", 5);
        g.setCondition("G");
        CardReservation carlito = reservedReservation(
                "Academy Manufactor",
                "Bloomburrow Commander Decks",
                "BLC",
                "0264",
                "EX",
                ""
        );
        carlito.setId("carlito-blc-ex");
        when(inventoryService.getReservations()).thenReturn(List.of(carlito));
        when(inventoryService.getInventoryCards()).thenReturn(List.of(ex, g));

        DashboardController.StockSnapshot snapshot = stockSnapshot(ex, List.of(ex, g), List.of(carlito));

        for (ReservationConditionStock stock : snapshot.conditionStocks()) {
            if (stock.reservedQuantity() <= 0) {
                continue;
            }

            ResponseEntity<List<DashboardController.PendingReservationView>> response =
                    controller.pendingReservationsForCard(
                            "Academy Manufactor",
                            "Bloomburrow Commander Decks",
                            "BLC",
                            "0264",
                            "nonfoil",
                            stock.condition(),
                            stock.rowIndex(),
                            request
                    );
            assertThat(response.getBody()).isEmpty();
        }
    }

    @Test
    void createReservationFromSearchRemoveFromStockUsesLedgerAndKeepsPhysicalQuantity() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();
        List<CardReservation> savedReservations = new ArrayList<>();

        InventoryCard nm = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 10);
        nm.setCondition("NM");
        InventoryCard g = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 11);
        g.setCondition("G");
        when(inventoryService.getInventoryCards()).thenReturn(List.of(nm, g));
        when(inventoryService.getReservations()).thenAnswer(invocation -> new ArrayList<>(savedReservations));
        doAnswer(invocation -> {
            savedReservations.add(invocation.getArgument(0));
            return null;
        }).when(inventoryService).appendReservation(any(CardReservation.class));

        ResponseEntity<?> response = controller.createReservationFromSearch(
                CardReservation.STATUS_WANTED,
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "",
                "1",
                "Sofi",
                "111",
                "222",
                "2026-06-30",
                false,
                "",
                true,
                10,
                true,
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(savedReservations).hasSize(1);
        CardReservation saved = savedReservations.get(0);
        assertThat(saved.getStatus()).isEqualTo(CardReservation.STATUS_RESERVED);
        assertThat(saved.getCondition()).isIn("NM", "G");
        assertThat(nm.getQuantity()).isEqualTo("1");
        assertThat(g.getQuantity()).isEqualTo("1");

        DashboardController.ReservationCreateResponse body =
                (DashboardController.ReservationCreateResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.snapshot().stockTotal()).isEqualTo(2);
        assertThat(body.snapshot().reservedQuantity()).isEqualTo(1);
        assertThat(body.snapshot().availableQuantity()).isEqualTo(1);
    }

    @Test
    void createReservationFromSearchWithoutRemoveFromStockDoesNotReserveStock() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();
        List<CardReservation> savedReservations = new ArrayList<>();

        InventoryCard vg = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 9);
        vg.setCondition("VG");
        when(inventoryService.getInventoryCards()).thenReturn(List.of(vg));
        when(inventoryService.getReservations()).thenAnswer(invocation -> new ArrayList<>(savedReservations));
        doAnswer(invocation -> {
            savedReservations.add(invocation.getArgument(0));
            return null;
        }).when(inventoryService).appendReservation(any(CardReservation.class));

        ResponseEntity<?> response = controller.createReservationFromSearch(
                CardReservation.STATUS_WANTED,
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "VG",
                "1",
                "Sofi",
                "111",
                "222",
                "2026-06-30",
                false,
                "",
                false,
                9,
                false,
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(savedReservations).hasSize(1);
        assertThat(savedReservations.get(0).getStatus()).isEqualTo(CardReservation.STATUS_WANTED);
        DashboardController.ReservationCreateResponse body =
                (DashboardController.ReservationCreateResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.snapshot().stockTotal()).isEqualTo(1);
        assertThat(body.snapshot().reservedQuantity()).isZero();
        assertThat(body.snapshot().availableQuantity()).isEqualTo(1);
        verify(inventoryService, never()).updateStockState(anyInt(), any());
    }

    @Test
    void createReservationFromSearchAcceptsCompleteCardIdentityPayload() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();
        List<CardReservation> savedReservations = new ArrayList<>();

        InventoryCard ex = inventoryCard(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "1",
                "En Stock",
                8
        );
        ex.setCondition("EX");
        when(inventoryService.getInventoryCards()).thenReturn(List.of(ex));
        when(inventoryService.getReservations()).thenAnswer(invocation -> new ArrayList<>(savedReservations));
        doAnswer(invocation -> {
            savedReservations.add(invocation.getArgument(0));
            return null;
        }).when(inventoryService).appendReservation(any(CardReservation.class));

        ResponseEntity<?> response = controller.createReservationFromSearch(
                "Sin Stock",
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "EX",
                "1",
                "Raul",
                "1152356589",
                "41523568",
                "2026-07-06",
                false,
                "",
                true,
                8,
                false,
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(DashboardController.ReservationCreateResponse.class);
        assertThat(((DashboardController.ReservationCreateResponse) response.getBody()).message())
                .doesNotContain("Completa carta");
        assertThat(savedReservations).hasSize(1);
        assertThat(savedReservations.get(0).getName()).isEqualTo("Academy Manufactor");
    }

    @Test
    void createFlexibleReservationWithRemoveFromStockHonorsSelectedRow() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();
        List<CardReservation> savedReservations = new ArrayList<>();

        InventoryCard mocEx = inventoryCard(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "2",
                "En Stock",
                8
        );
        mocEx.setCondition("EX");
        InventoryCard blcG = inventoryCard(
                "Academy Manufactor",
                "Bloomburrow Commander Decks",
                "BLC",
                "0264",
                "nonfoil",
                "2",
                "En Stock",
                9
        );
        blcG.setCondition("G");
        when(inventoryService.getInventoryCards()).thenReturn(List.of(mocEx, blcG));
        when(inventoryService.getReservations()).thenAnswer(invocation -> new ArrayList<>(savedReservations));
        doAnswer(invocation -> {
            savedReservations.add(invocation.getArgument(0));
            return null;
        }).when(inventoryService).appendReservation(any(CardReservation.class));

        ResponseEntity<?> response = controller.createReservationFromSearch(
                "Sin Stock",
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "EX",
                "1",
                "Codex Live",
                "1152356589",
                "41523568",
                "2026-07-06",
                false,
                "",
                true,
                8,
                true,
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(savedReservations).hasSize(1);
        CardReservation saved = savedReservations.get(0);
        assertThat(saved.getStatus()).isEqualTo(CardReservation.STATUS_RESERVED);
        assertThat(saved.getNotes()).contains("[Cualquier edicion/condicion]");
        assertThat(saved.getSetCode()).isEqualTo("MOC");
        assertThat(saved.getCollectorNumber()).isEqualTo("0346");
        assertThat(saved.getCondition()).isEqualTo("EX");
        DashboardController.ReservationCreateResponse body =
                (DashboardController.ReservationCreateResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.reservedQuantity()).isEqualTo(1);
        assertThat(body.availableQuantity()).isEqualTo(1);
        assertThat(body.rowIndex()).isEqualTo(8);
        assertThat(body.snapshot().reservedQuantity()).isEqualTo(1);
        assertThat(body.snapshot().availableQuantity()).isEqualTo(1);
        verify(inventoryService).updateStockState(eq(8), same(mocEx));
        verify(inventoryService, never()).updateStockState(eq(9), any());
    }

    @Test
    void createFlexibleReservationWithRemoveFromStockHonorsSelectedCondition() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();

        InventoryCard mocNm = inventoryCard(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "2",
                "En Stock",
                7
        );
        mocNm.setCondition("NM");
        InventoryCard mocEx = inventoryCard(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "2",
                "Reservada",
                8
        );
        mocEx.setCondition("EX");
        InventoryCard mocVg = inventoryCard(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "1",
                "En Stock",
                9
        );
        mocVg.setCondition("VG");

        CardReservation firstEx = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "EX",
                ""
        );
        CardReservation secondEx = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "EX",
                ""
        );
        List<CardReservation> savedReservations = new ArrayList<>(List.of(firstEx, secondEx));

        when(inventoryService.getInventoryCards()).thenReturn(List.of(mocNm, mocEx, mocVg));
        when(inventoryService.getReservations()).thenAnswer(invocation -> new ArrayList<>(savedReservations));
        doAnswer(invocation -> {
            savedReservations.add(invocation.getArgument(0));
            return null;
        }).when(inventoryService).appendReservation(any(CardReservation.class));

        ResponseEntity<?> response = controller.createReservationFromSearch(
                "Sin Stock",
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "VG",
                "1",
                "Codex Test",
                "1152356589",
                "41523568",
                "2026-07-06",
                false,
                "",
                true,
                9,
                true,
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardController.ReservationCreateResponse body =
                (DashboardController.ReservationCreateResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.rowIndex()).isEqualTo(9);
        assertThat(body.reservedQuantity()).isEqualTo(1);
        assertThat(body.availableQuantity()).isZero();
        assertThat(body.snapshot().stockTotal()).isEqualTo(5);
        assertThat(body.snapshot().reservedQuantity()).isEqualTo(3);
        assertThat(body.snapshot().availableQuantity()).isEqualTo(2);

        ReservationConditionStock nmStock = body.snapshot().conditionStocks().stream()
                .filter(stock -> stock.condition().equals("NM"))
                .findFirst()
                .orElseThrow();
        ReservationConditionStock exStock = body.snapshot().conditionStocks().stream()
                .filter(stock -> stock.condition().equals("EX"))
                .findFirst()
                .orElseThrow();
        ReservationConditionStock vgStock = body.snapshot().conditionStocks().stream()
                .filter(stock -> stock.condition().equals("VG"))
                .findFirst()
                .orElseThrow();

        assertThat(nmStock.reservedQuantity()).isZero();
        assertThat(nmStock.availableQuantity()).isEqualTo(2);
        assertThat(exStock.reservedQuantity()).isEqualTo(2);
        assertThat(exStock.availableQuantity()).isZero();
        assertThat(vgStock.reservedQuantity()).isEqualTo(1);
        assertThat(vgStock.availableQuantity()).isZero();
        assertThat(vgStock.action()).isEqualTo("Reservada");

        verify(inventoryService).updateStockState(eq(9), same(mocVg));
        verify(inventoryService, never()).updateStockState(eq(7), any());
    }

    @Test
    void createFlexibleReservationWithRemoveFromStockRejectsWhenSelectedRowHasNoAvailableStock() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();

        InventoryCard mocEx = inventoryCard(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "0",
                "Sin Stock",
                8
        );
        mocEx.setCondition("EX");
        InventoryCard blcG = inventoryCard(
                "Academy Manufactor",
                "Bloomburrow Commander Decks",
                "BLC",
                "0264",
                "nonfoil",
                "2",
                "En Stock",
                9
        );
        blcG.setCondition("G");
        when(inventoryService.getInventoryCards()).thenReturn(List.of(mocEx, blcG));
        when(inventoryService.getReservations()).thenReturn(List.of());

        ResponseEntity<?> response = controller.createReservationFromSearch(
                "Sin Stock",
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "EX",
                "1",
                "Codex Live",
                "1152356589",
                "41523568",
                "2026-07-06",
                false,
                "",
                true,
                8,
                true,
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(DashboardController.ApiMessage.class);
        assertThat(((DashboardController.ApiMessage) response.getBody()).message())
                .contains("No hay stock suficiente");
        verify(inventoryService, never()).appendReservation(any(CardReservation.class));
        verify(inventoryService, never()).updateStockState(anyInt(), any());
    }

    @Test
    void createFlexibleReservationWithoutRemoveFromStockStaysPendingAndDoesNotReserveStock() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();
        List<CardReservation> savedReservations = new ArrayList<>();

        InventoryCard mocEx = inventoryCard(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "1",
                "En Stock",
                8
        );
        mocEx.setCondition("EX");
        when(inventoryService.getInventoryCards()).thenReturn(List.of(mocEx));
        when(inventoryService.getReservations()).thenAnswer(invocation -> new ArrayList<>(savedReservations));
        doAnswer(invocation -> {
            savedReservations.add(invocation.getArgument(0));
            return null;
        }).when(inventoryService).appendReservation(any(CardReservation.class));

        ResponseEntity<?> response = controller.createReservationFromSearch(
                "Sin Stock",
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "nonfoil",
                "EX",
                "1",
                "Codex Pending",
                "1152356589",
                "41523568",
                "2026-07-06",
                false,
                "",
                true,
                8,
                false,
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(savedReservations).hasSize(1);
        assertThat(savedReservations.get(0).getStatus()).isEqualTo(CardReservation.STATUS_WANTED);
        DashboardController.ReservationCreateResponse body =
                (DashboardController.ReservationCreateResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.reservedQuantity()).isZero();
        assertThat(body.snapshot().reservedQuantity()).isZero();
        assertThat(body.snapshot().availableQuantity()).isEqualTo(1);
        assertThat(body.pendingInfo().pending()).isTrue();
        assertThat(body.pendingInfo().quantity()).isEqualTo(1);
        assertThat(body.pendingInfo().clientsLabel()).contains("Codex Pending");
        verify(inventoryService, never()).updateStockState(anyInt(), any());
    }

    @Test
    void createExactNoStockPendingReservationReturnsFamilyPendingInfo() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();
        List<CardReservation> savedReservations = new ArrayList<>();

        when(inventoryService.getInventoryCards()).thenReturn(List.of());
        when(inventoryService.getReservations()).thenAnswer(invocation -> new ArrayList<>(savedReservations));
        doAnswer(invocation -> {
            savedReservations.add(invocation.getArgument(0));
            return null;
        }).when(inventoryService).appendReservation(any(CardReservation.class));

        ResponseEntity<?> response = controller.createReservationFromSearch(
                "Sin Stock",
                "Academy Manufactor",
                "Murders at Karlov Manor Commander Decks",
                "MKC",
                "0221",
                "No Foil",
                "NM",
                "1",
                "Maria",
                "1152356589",
                "41523568",
                "2026-07-06",
                false,
                "",
                false,
                0,
                false,
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardController.ReservationCreateResponse body =
                (DashboardController.ReservationCreateResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.rowIndex()).isZero();
        assertThat(body.pendingInfo().quantity()).isEqualTo(1);
        assertThat(body.familyPendingInfo().pending()).isTrue();
        assertThat(body.familyPendingInfo().quantity()).isEqualTo(1);
        assertThat(body.familyPendingInfo().summaryLabel()).isEqualTo("Pedido pendiente: 1");
        assertThat(body.familyPendingInfo().tooltip()).contains("Maria - MKC-0221 NM");
        verify(inventoryService, never()).updateStockState(anyInt(), any());
    }

    @Test
    void createExactNoStockPendingReservationFamilyInfoIncludesExistingExactClients() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        HttpServletRequest request = unlockedRequest();

        CardReservation maria = reservedReservation(
                "Academy Manufactor",
                "Murders at Karlov Manor Commander Decks",
                "MKC",
                "0221",
                "NM",
                ""
        );
        maria.setStatus(CardReservation.STATUS_WANTED);
        maria.setClient("Maria");
        maria.setPrinting("No Foil");
        List<CardReservation> savedReservations = new ArrayList<>(List.of(maria));

        when(inventoryService.getInventoryCards()).thenReturn(List.of());
        when(inventoryService.getReservations()).thenAnswer(invocation -> new ArrayList<>(savedReservations));
        doAnswer(invocation -> {
            savedReservations.add(invocation.getArgument(0));
            return null;
        }).when(inventoryService).appendReservation(any(CardReservation.class));

        ResponseEntity<?> response = controller.createReservationFromSearch(
                "Sin Stock",
                "Academy Manufactor",
                "Murders at Karlov Manor Commander Decks",
                "MKC",
                "0221",
                "No Foil",
                "NM",
                "1",
                "Raul",
                "1152356589",
                "41523568",
                "2026-07-06",
                false,
                "",
                false,
                0,
                false,
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardController.ReservationCreateResponse body =
                (DashboardController.ReservationCreateResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.familyPendingInfo().quantity()).isEqualTo(2);
        assertThat(body.familyPendingInfo().summaryLabel()).isEqualTo("Pedidos pendientes: 2");
        assertThat(body.familyPendingInfo().tooltip())
                .contains("Maria - MKC-0221 NM")
                .contains("Raul - MKC-0221 NM");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pickupAlertsCountOnlyLedgerAssignedReservedStockWhenInventoryIsAvailable() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("pickupAlerts", List.class, List.class);
        method.setAccessible(true);

        CardReservation assigned = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "",
                "[Cualquier edicion/condicion]"
        );
        assigned.setClient("Sofi");
        assigned.setPhone("111");
        assigned.setDni("222");
        assigned.setPickupDate("2026-06-19");
        CardReservation unassigned = reservedReservation(
                "Sol Ring",
                "Commander Legends",
                "CMR",
                "334",
                "NM",
                ""
        );
        unassigned.setClient("Sofi");
        unassigned.setPhone("111");
        unassigned.setDni("222");
        unassigned.setPickupDate("2026-06-19");
        InventoryCard stock = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 10);
        stock.setCondition("NM");

        List<DashboardController.PickupAlertView> alerts =
                (List<DashboardController.PickupAlertView>) method.invoke(
                        controller,
                        List.of(assigned, unassigned),
                        List.of(stock)
                );

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).totalQuantity()).isEqualTo(2);
        assertThat(alerts.get(0).reservedQuantity()).isEqualTo(1);
    }

    @Test
    void updateResultFromCardUsesFamilyLedgerInsteadOfSingleRow() throws Exception {
        InventoryService inventoryService = mock(InventoryService.class);
        DashboardController controller = new DashboardController(inventoryService, null, null, null, null, null);
        Method method = DashboardController.class.getDeclaredMethod("updateResultFromCard", InventoryCard.class);
        method.setAccessible(true);

        InventoryCard nm = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 10);
        nm.setCondition("NM");
        InventoryCard g = inventoryCard("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "nonfoil", "1", "En Stock", 11);
        g.setCondition("G");
        CardReservation flexible = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "",
                "[Cualquier edicion/condicion]"
        );
        when(inventoryService.getInventoryCards()).thenReturn(List.of(nm, g));
        when(inventoryService.getReservations()).thenReturn(List.of(flexible));

        DashboardController.UpdateResult update =
                (DashboardController.UpdateResult) method.invoke(controller, nm);

        assertThat(update.conditionStocks()).hasSize(2);
        assertThat(update.displayStockQuantity()).isEqualTo(2);
        assertThat(update.displayReservedQuantity()).isEqualTo(1);
        assertThat(update.displayAvailableQuantity()).isEqualTo(1);
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
    void productAggregationCountsFlexibleReservedStockAtGlobalLevel() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("groupedSearchResults", List.class, List.class);
        method.setAccessible(true);

        DashboardController.SearchResult moc = searchFamilyWithConditionStocks(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                List.of(
                        new ReservationConditionStock("NM", 3, 1, 2, "Reservada", 10, "1.00", "1000"),
                        new ReservationConditionStock("EX", 2, 2, 0, "En Stock", 11, "0.80", "800")
                )
        );
        DashboardController.SearchResult blc = searchFamilyWithConditionStocks(
                "Academy Manufactor",
                "Bloomburrow Commander Decks",
                "BLC",
                "0264",
                List.of(new ReservationConditionStock("G", 6, 6, 0, "En Stock", 12, "0.70", "700"))
        );
        List<CardReservation> reservations = List.of(
                reservedReservation("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "NM", ""),
                reservedReservation("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "NM", ""),
                reservedReservation("Academy Manufactor", "", "", "", "", "[Cualquier edicion/condicion]")
        );

        List<DashboardController.SearchProductGroup> groups =
                (List<DashboardController.SearchProductGroup>) method.invoke(controller, List.of(moc, blc), reservations);

        assertThat(groups).hasSize(1);
        DashboardController.SearchProductGroup group = groups.get(0);
        assertThat(group.stockQuantity()).isEqualTo(11);
        assertThat(group.reservedQuantity()).isEqualTo(3);
        assertThat(group.availableQuantity()).isEqualTo(8);
        DashboardController.SearchResult mocRow = group.families().stream()
                .filter(family -> family.setCode().equals("MOC"))
                .findFirst()
                .orElseThrow();
        assertThat(mocRow.displayStockQuantity()).isEqualTo(5);
        assertThat(mocRow.displayReservedQuantity()).isEqualTo(2);
        assertThat(mocRow.displayAvailableQuantity()).isEqualTo(3);
    }

    @Test
    @SuppressWarnings("unchecked")
    void productAggregationShowsFlexibleWantedReservationAsPendingWithoutReservedStock() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("groupedSearchResults", List.class, List.class);
        method.setAccessible(true);

        DashboardController.SearchResult moc = searchFamilyWithConditionStocks(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                List.of(new ReservationConditionStock("NM", 5, 3, 2, "Reservada", 10, "1.00", "1000"))
        );
        DashboardController.SearchResult blc = searchFamilyWithConditionStocks(
                "Academy Manufactor",
                "Bloomburrow Commander Decks",
                "BLC",
                "0264",
                List.of(new ReservationConditionStock("G", 6, 6, 0, "En Stock", 12, "0.70", "700"))
        );
        CardReservation wantedFlexible = reservedReservation(
                "Academy Manufactor",
                "",
                "",
                "",
                "",
                "[Cualquier edicion/condicion]"
        );
        wantedFlexible.setStatus(CardReservation.STATUS_WANTED);
        wantedFlexible.setClient("Pepito");
        List<CardReservation> reservations = List.of(
                reservedReservation("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "NM", ""),
                reservedReservation("Academy Manufactor", "March of the Machine Commander Decks", "MOC", "0346", "NM", ""),
                wantedFlexible
        );

        List<DashboardController.SearchProductGroup> groups =
                (List<DashboardController.SearchProductGroup>) method.invoke(controller, List.of(moc, blc), reservations);

        assertThat(groups).hasSize(1);
        DashboardController.SearchProductGroup group = groups.get(0);
        assertThat(group.stockQuantity()).isEqualTo(11);
        assertThat(group.reservedQuantity()).isEqualTo(2);
        assertThat(group.availableQuantity()).isEqualTo(9);
        assertThat(group.pendingInfo().getQuantity()).isEqualTo(1);
        assertThat(group.pendingInfo().getSummaryLabel()).isEqualTo("Pedido pendiente: 1");
        assertThat(group.pendingInfo().getClientsLabel()).contains("Pepito");
        assertThat(group.pendingInfo().getTooltip()).contains("Pepito - cualquier edicion/condicion");
        assertThat(group.families())
                .allSatisfy(family -> assertThat(family.displayReservedQuantity()).isLessThanOrEqualTo(2));
    }

    @Test
    @SuppressWarnings("unchecked")
    void productAggregationShowsExactWantedReservationAsPendingWithoutReservedStock() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("groupedSearchResults", List.class, List.class);
        method.setAccessible(true);

        DashboardController.SearchResult fmh2 = searchFamilyWithConditionStocks(
                "Academy Manufactor",
                "Modern Horizons 2 Promos",
                "FMH2",
                "469",
                List.of(new ReservationConditionStock("NM", 0, 0, 0, "Sin Stock", 20, "1.00", "1000"))
        );
        CardReservation exactWanted = reservedReservation(
                "Academy Manufactor",
                "Modern Horizons 2 Promos",
                "FMH2",
                "469",
                "NM",
                ""
        );
        exactWanted.setStatus(CardReservation.STATUS_WANTED);
        exactWanted.setClient("Soky");
        exactWanted.setPrinting("foil");

        List<DashboardController.SearchProductGroup> groups =
                (List<DashboardController.SearchProductGroup>) method.invoke(controller, List.of(fmh2), List.of(exactWanted));

        assertThat(groups).hasSize(1);
        DashboardController.SearchProductGroup group = groups.get(0);
        assertThat(group.stockQuantity()).isZero();
        assertThat(group.reservedQuantity()).isZero();
        assertThat(group.availableQuantity()).isZero();
        assertThat(group.pendingInfo().getQuantity()).isEqualTo(1);
        assertThat(group.pendingInfo().getSummaryLabel()).isEqualTo("Pedido pendiente: 1");
        assertThat(group.pendingInfo().getClientsLabel()).contains("Soky");
        assertThat(group.pendingInfo().getTooltip()).contains("Soky - FMH2-469 NM Foil");
    }

    @Test
    @SuppressWarnings("unchecked")
    void productAggregationCombinesFlexibleAndExactWantedReservationsAsPending() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("groupedSearchResults", List.class, List.class);
        method.setAccessible(true);

        DashboardController.SearchResult moc = searchFamilyWithConditionStocks(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                List.of(new ReservationConditionStock("EX", 5, 5, 0, "En Stock", 10, "1.00", "1000"))
        );
        DashboardController.SearchResult fmh2 = searchFamilyWithConditionStocks(
                "Academy Manufactor",
                "Modern Horizons 2 Promos",
                "FMH2",
                "469",
                List.of(new ReservationConditionStock("NM", 0, 0, 0, "Sin Stock", 20, "1.00", "1000"))
        );
        CardReservation flexibleWanted = reservedReservation("Academy Manufactor", "", "", "", "", "[Cualquier edicion/condicion]");
        flexibleWanted.setStatus(CardReservation.STATUS_WANTED);
        flexibleWanted.setClient("Pepito");
        CardReservation exactWanted = reservedReservation("Academy Manufactor", "Modern Horizons 2 Promos", "FMH2", "469", "NM", "");
        exactWanted.setStatus(CardReservation.STATUS_WANTED);
        exactWanted.setClient("Soky");
        exactWanted.setPrinting("foil");

        List<DashboardController.SearchProductGroup> groups =
                (List<DashboardController.SearchProductGroup>) method.invoke(
                        controller,
                        List.of(moc, fmh2),
                        List.of(flexibleWanted, exactWanted)
                );

        assertThat(groups).hasSize(1);
        DashboardController.SearchProductGroup group = groups.get(0);
        assertThat(group.reservedQuantity()).isZero();
        assertThat(group.availableQuantity()).isEqualTo(5);
        assertThat(group.pendingInfo().getQuantity()).isEqualTo(2);
        assertThat(group.pendingInfo().getSummaryLabel()).isEqualTo("Pedidos pendientes: 2");
        assertThat(group.pendingInfo().getTooltip())
                .contains("Pepito - cualquier edicion/condicion")
                .contains("Soky - FMH2-469 NM Foil");
    }

    @Test
    @SuppressWarnings("unchecked")
    void productAggregationDoesNotShowReservedStockAsPending() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("groupedSearchResults", List.class, List.class);
        method.setAccessible(true);

        DashboardController.SearchResult moc = searchFamilyWithConditionStocks(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                List.of(new ReservationConditionStock("EX", 5, 4, 1, "Reservada", 10, "1.00", "1000"))
        );
        CardReservation reserved = reservedReservation(
                "Academy Manufactor",
                "March of the Machine Commander Decks",
                "MOC",
                "0346",
                "EX",
                ""
        );
        reserved.setClient("Raul");

        List<DashboardController.SearchProductGroup> groups =
                (List<DashboardController.SearchProductGroup>) method.invoke(controller, List.of(moc), List.of(reserved));

        assertThat(groups).hasSize(1);
        DashboardController.SearchProductGroup group = groups.get(0);
        assertThat(group.reservedQuantity()).isEqualTo(1);
        assertThat(group.availableQuantity()).isEqualTo(4);
        assertThat(group.pendingInfo().getQuantity()).isZero();
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
    void searchConditionStockDisplayUsesReservedActionWhenPartiallyReserved() throws Exception {
        Method method = DashboardController.class.getDeclaredMethod("groupedSearchResults", List.class, List.class);
        method.setAccessible(true);

        List<ReservationConditionStock> conditionStocks = List.of(
                new ReservationConditionStock("NM", 3, 2, 1, "En Stock", 10, "8.49", "14500")
        );
        DashboardController.SearchResult result = searchResult("NM", conditionStocks, 3, 10);

        List<DashboardController.SearchProductGroup> groups =
                (List<DashboardController.SearchProductGroup>) method.invoke(controller, List.of(result), List.of());

        DashboardController.SearchResult displayed = groups.get(0).families().get(0);
        assertThat(displayed.displayStockQuantity()).isEqualTo(3);
        assertThat(displayed.displayReservedQuantity()).isEqualTo(1);
        assertThat(displayed.displayAvailableQuantity()).isEqualTo(2);
        assertThat(displayed.conditionStocks().get(0).action()).isEqualTo("En Stock");
        assertThat(displayed.displayConditionStocks().get(0).action()).isEqualTo("Reservada");
    }

    @Test
    void searchParentStatusUsesHomeDisplayRuleWhenPartiallyReserved() {
        List<ReservationConditionStock> conditionStocks = List.of(
                new ReservationConditionStock("NM", 3, 2, 1, "En Stock", 10, "8.49", "14500")
        );

        DashboardController.SearchResult result = searchResult("NM", conditionStocks, 3, 10);

        assertThat(result.displayStockQuantity()).isEqualTo(3);
        assertThat(result.displayReservedQuantity()).isEqualTo(1);
        assertThat(result.displayAvailableQuantity()).isEqualTo(2);
        assertThat(result.displayAction()).isEqualTo("Reservada");
        assertThat(result.displayActionClass()).isEqualTo("reservada");
    }

    @Test
    @SuppressWarnings("unchecked")
    void groupedLatestUpdatesSkipsBlankNameRowsFromHomeRenderModel() throws Exception {
        Field latestUpdates = DashboardController.class.getDeclaredField("latestUpdates");
        latestUpdates.setAccessible(true);
        latestUpdates.set(controller, List.of(
                update("", "Bloomburrow Commander Decks", "BLC", "0264", "En Stock", 1, 2),
                update("Academy Manufactor", "Bloomburrow Commander Decks", "BLC", "0264", "En Stock", 1, 3)
        ));

        Method method = DashboardController.class.getDeclaredMethod("groupedLatestUpdates");
        method.setAccessible(true);

        List<DashboardController.UpdateResult> updates =
                (List<DashboardController.UpdateResult>) method.invoke(controller);

        assertThat(updates)
                .extracting(DashboardController.UpdateResult::name)
                .containsExactly("Academy Manufactor");
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
        assertThat(first.getCurrentStock()).isEqualTo(1);
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

    private DashboardController.StockSnapshot stockSnapshot(
            InventoryCard card,
            List<InventoryCard> inventoryCards,
            List<CardReservation> reservations
    ) throws Exception {
        Method method = DashboardController.class.getDeclaredMethod(
                "stockSnapshot",
                InventoryCard.class,
                List.class,
                List.class
        );
        method.setAccessible(true);
        return (DashboardController.StockSnapshot) method.invoke(controller, card, inventoryCards, reservations);
    }

    private HttpServletRequest unlockedRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("movementsAccessUnlocked")).thenReturn(Boolean.TRUE);
        return request;
    }

    private ReservationClient reservationClient(String client, String phone, String dni) {
        ReservationClient reservationClient = new ReservationClient();
        reservationClient.setClient(client);
        reservationClient.setPhone(phone);
        reservationClient.setDni(dni);
        return reservationClient;
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

    private DashboardController.SearchResult searchFamilyWithConditionStocks(
            String name,
            String edition,
            String setCode,
            String collectorNumber,
            List<ReservationConditionStock> conditionStocks
    ) {
        int nmQuantity = conditionStocks.stream()
                .filter(stock -> stock.condition().equals("NM"))
                .mapToInt(ReservationConditionStock::quantity)
                .findFirst()
                .orElse(0);
        int exQuantity = conditionStocks.stream()
                .filter(stock -> stock.condition().equals("EX"))
                .mapToInt(ReservationConditionStock::quantity)
                .findFirst()
                .orElse(0);
        int vgQuantity = conditionStocks.stream()
                .filter(stock -> stock.condition().equals("VG"))
                .mapToInt(ReservationConditionStock::quantity)
                .findFirst()
                .orElse(0);
        int gQuantity = conditionStocks.stream()
                .filter(stock -> stock.condition().equals("G"))
                .mapToInt(ReservationConditionStock::quantity)
                .findFirst()
                .orElse(0);
        int rowIndex = conditionStocks.stream()
                .mapToInt(ReservationConditionStock::rowIndex)
                .filter(index -> index > 0)
                .findFirst()
                .orElse(0);

        return new DashboardController.SearchResult(
                name,
                edition,
                setCode + "-" + collectorNumber,
                setCode,
                collectorNumber,
                "-",
                "No Foil",
                "NM",
                "1.00",
                "0.80",
                "0.70",
                "0.60",
                "1000",
                "800",
                "700",
                "600",
                nmQuantity,
                exQuantity,
                vgQuantity,
                gQuantity,
                rowIndex,
                rowIndex,
                rowIndex,
                rowIndex,
                conditionStocks,
                "1000",
                conditionStocks.stream().mapToInt(ReservationConditionStock::quantity).sum(),
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
