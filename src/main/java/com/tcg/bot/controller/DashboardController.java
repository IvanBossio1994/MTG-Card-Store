package com.tcg.bot.controller;

import com.tcg.bot.dto.CardKingdomProduct;
import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.InventoryMovement;
import com.tcg.bot.service.CardKingdomApiService;
import com.tcg.bot.service.InventoryService;
import com.tcg.bot.service.PriceComparisonService;
import com.tcg.bot.service.PricingSettingsService;
import com.tcg.bot.service.StoreSettingsService;
import com.tcg.bot.service.StockOperationsService;
import com.tcg.bot.service.LicenseService;
import com.tcg.bot.service.ScryfallPriceService;
import com.tcg.bot.util.Amounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final InventoryService inventoryService;
    private final CardKingdomApiService cardKingdomApiService;
    private final PriceComparisonService priceComparisonService;
    private final PricingSettingsService pricingSettingsService;
    private final StoreSettingsService storeSettingsService;
    private volatile List<UpdateResult> latestUpdates = List.of();
    private volatile long latestUpdatedCount;
    private static final Pattern LEADING_QUANTITY_PATTERN =
            Pattern.compile("^\\s*(\\d+)\\s*x?\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_QUANTITY_PATTERN =
            Pattern.compile("^\\s*(.+?)\\s+x\\s*(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SET_CODE_PATTERN =
            Pattern.compile("[\\[(]([A-Za-z0-9]{2,8})[\\])]");
    private static final Pattern COLLECTOR_PATTERN =
            Pattern.compile("(?:#|\\s)([A-Za-z0-9]+(?:-[A-Za-z0-9]+)?)\\s*$");
    private static final String ACTION_IN_STOCK = "CON STOCK";
    private static final String ACTION_OUT_OF_STOCK = "SIN STOCK";
    private static final String ACTION_NO_CK_MATCH = "SIN MATCH CK";
    private static final String ACTION_NO_CK_PRICE = "SIN PRECIO CK";
    private static final Locale ARGENTINA_LOCALE = new Locale("es", "AR");
    private static final ZoneId APP_ZONE = ZoneId.of("America/Buenos_Aires");
    private static final DateTimeFormatter MOVEMENT_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MOVEMENT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MOVEMENT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final StockOperationsService stockOperationsService;
    private final LicenseService licenseService;
    private final ScryfallPriceService scryfallPriceService;
    private final com.tcg.bot.service.PriceHistoryService priceHistoryService;
    private final com.tcg.bot.service.StoreSummaryService storeSummaryService;
    private final com.tcg.bot.service.ReviewService reviewService;
    private final com.tcg.bot.service.BuyService buyService;

    public DashboardController(
            InventoryService inventoryService,
            CardKingdomApiService cardKingdomApiService,
            PriceComparisonService priceComparisonService,
            PricingSettingsService pricingSettingsService,
            StoreSettingsService storeSettingsService,
            StockOperationsService stockOperationsService,
            LicenseService licenseService,
            ScryfallPriceService scryfallPriceService,
            com.tcg.bot.service.PriceHistoryService priceHistoryService,
            com.tcg.bot.service.StoreSummaryService storeSummaryService,
            com.tcg.bot.service.ReviewService reviewService,
            com.tcg.bot.service.BuyService buyService
    ) {
        this.storeSummaryService = storeSummaryService;
        this.reviewService = reviewService;
        this.buyService = buyService;
        this.priceHistoryService = priceHistoryService;
        this.scryfallPriceService = scryfallPriceService;
        this.stockOperationsService = stockOperationsService;
        this.licenseService = licenseService;
        this.inventoryService = inventoryService;
        this.cardKingdomApiService = cardKingdomApiService;
        this.priceComparisonService = priceComparisonService;
        this.pricingSettingsService = pricingSettingsService;
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping("/")
    public String dashboard(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "set", required = false) String setFilter,
            @RequestParam(name = "number", required = false) String numberFilter,
            Model model
    ) {
        addBaseModel(model, query);
        model.addAttribute("searchSet", setFilter == null ? "" : setFilter);
        model.addAttribute("searchNumber", numberFilter == null ? "" : numberFilter);

        model.addAttribute(
                "showTutorial",
                !storeSettingsService.isTutorialCompleted()
        );

        boolean searchRequested = query != null || setFilter != null || numberFilter != null;

        if (isBlank(query) && isBlank(setFilter) && isBlank(numberFilter)) {
            if (searchRequested) {
                model.addAttribute("searchFormatError", "Completa al menos un filtro para buscar.");
            }

            addLatestUpdates(model);
            if (storeSettingsService.hasSpreadsheetConfigured() && inventoryService.hasCredentialsConfigured()) {
                model.addAttribute("summary", storeSummaryService.summary());
                try {
                    model.addAttribute("reviewCount", reviewService.review().total());
                } catch (Exception e) {
                    log.debug("No se pudo calcular lo que hay para revisar: {}", e.getMessage());
                }
            }
            return "dashboard";
        }

        model.addAttribute("searchPerformed", true);
        model.addAttribute("results", List.of());
        model.addAttribute("totalStockQuantity", 0);

        String trimmedQuery = query == null ? "" : query.trim();
        String trimmedSet = setFilter == null ? "" : setFilter.trim();
        String trimmedNumber = numberFilter == null ? "" : numberFilter.trim();

        if (!isValidSearchQuery(trimmedQuery, trimmedSet, trimmedNumber)) {
            model.addAttribute(
                    "searchFormatError",
                    "Formato invalido. Busca por nombre (ej: Sol Ring) o usa: Sol Ring, cmm, Commander Masters, 410"
            );
            return "dashboard";
        }

        try {
            var products = cardKingdomApiService.searchProducts(buildSearchQuery(trimmedQuery, trimmedSet, trimmedNumber));

            if (!trimmedQuery.isBlank() && trimmedSet.isBlank() && trimmedNumber.isBlank() && products.isEmpty()) {
                model.addAttribute(
                        "searchFormatError",
                        "No se encontro esa carta o falta una coma. Para filtrar usa: Sol Ring, cmm."
                );
                return "dashboard";
            }

            var inventoryCards = inventoryService.getInventoryCards();
            Map<String, ScryfallPriceService.Prices> extraPrices = pricingSettingsService.usesExtraSources()
                    ? scryfallPriceService.prices(products.stream()
                            .limit(150)
                            .map(product -> new ScryfallPriceService.CardRef(setCode(product.getSku()), collectorNumber(product.getSku())))
                            .toList())
                    : Map.of();
            var results = products.stream()
                    .map(product -> createSearchResult(product, inventoryCards, extraPrices))
                    .toList();
            model.addAttribute("showTcgplayer", pricingSettingsService.isTcgplayerEnabled());
            model.addAttribute("showCardmarket", pricingSettingsService.isCardmarketEnabled());

            model.addAttribute("results", results);
            model.addAttribute("totalStockQuantity", results.stream()
                    .mapToInt(SearchResult::stockQuantity)
                    .sum());
        } catch (Exception e) {
            model.addAttribute("error", "No se pudo consultar Card Kingdom o el inventario configurado.");
        }

        return "dashboard";
    }

    private boolean isValidSearchQuery(String query, String setFilter, String numberFilter) {
        return !containsInvalidSearchCharacter(query)
                && !containsInvalidSearchCharacter(setFilter)
                && !containsInvalidSearchCharacter(numberFilter);
    }

    private boolean containsInvalidSearchCharacter(String value) {
        return value != null && value.contains("|");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String buildSearchQuery(String query, String setFilter, String numberFilter) {
        List<String> parts = new ArrayList<>();

        if (!query.isBlank()) {
            parts.add(query);
        }

        if (!setFilter.isBlank()) {
            parts.add("set:" + setFilter);
        }

        if (!numberFilter.isBlank()) {
            parts.add("num:" + numberFilter);
        }

        return String.join(", ", parts);
    }

    private boolean isValidSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }

        if (query.contains("|")) {
            return false;
        }

        String[] parts = query.split(",", -1);
        if (parts.length == 1) {
            return !parts[0].isBlank();
        }

        if (parts.length < 2 || parts.length > 5) {
            return false;
        }

        for (String part : parts) {
            if (part.isBlank()) {
                return false;
            }
        }

        if (parts.length == 5) {
            String printing = parts[4].trim();
            return printing.equalsIgnoreCase("foil") || printing.equalsIgnoreCase("nonfoil");
        }

        return true;
    }

    @GetMapping("/api/cartas/sugerencias")
    @ResponseBody
    public ResponseEntity<List<CardSuggestion>> cardSuggestions(
            @RequestParam(name = "q", required = false) String query
    ) {
        if (query == null || query.trim().length() < 3) {
            return ResponseEntity.ok(List.of());
        }

        try {
            String normalizedQuery = normalizeSuggestionText(query);
            var priceList = cardKingdomApiService.getPriceList();

            if (priceList == null || priceList.getData() == null) {
                return ResponseEntity.ok(List.of());
            }

            Map<String, CardSuggestion> suggestions = new LinkedHashMap<>();

            for (CardKingdomProduct product : priceList.getData()) {
                if (suggestions.size() >= 8) {
                    break;
                }

                String name = product.getName();
                if (name == null || name.isBlank()) {
                    continue;
                }

                String variation = product.getVariation();
                if (!matchesSuggestion(name, variation, normalizedQuery)) {
                    continue;
                }

                String key = name.toLowerCase();
                suggestions.putIfAbsent(key, new CardSuggestion(
                        name,
                        variation == null ? "" : variation
                ));
            }

            return ResponseEntity.ok(new ArrayList<>(suggestions.values()));
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    private boolean matchesSuggestion(String name, String variation, String normalizedQuery) {
        String normalizedName = normalizeSuggestionText(name);
        String normalizedVariation = normalizeSuggestionText(variation);

        if (normalizedName.contains(normalizedQuery)
                || normalizedVariation.contains(normalizedQuery)) {
            return true;
        }

        for (String faceName : normalizedQuery.split("/")) {
            String normalizedFace = normalizeSuggestionText(faceName);

            if (!normalizedFace.isBlank()
                    && (normalizedName.contains(normalizedFace)
                    || normalizedVariation.contains(normalizedFace))) {
                return true;
            }
        }

        return false;
    }

    private String normalizeSuggestionText(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase()
                .replace("//", "/")
                .replaceAll("\\s*/\\s*", "/")
                .replaceAll("[^a-z0-9/]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    @GetMapping("/configuracion")
    public String storeSettings(Model model) {
        addBaseModel(model, "");
        addStoreModel(model);
        return "store";
    }

    @GetMapping("/movimientos")
    public String movements(
            @RequestParam(name = "movementDate", required = false) String movementDate,
            @RequestParam(name = "cashDate", required = false) String cashDate,
            @RequestParam(name = "movementFilter", required = false) String movementFilter,
            @RequestParam(name = "cashFilter", required = false) String cashFilter,
            @RequestParam(name = "tab", required = false) String tab,
            Model model
    ) {
        addBaseModel(model, "");
        String selectedMovementDate = movementDate == null ? "" : movementDate.trim();
        String selectedCashDate = cashDate == null ? "" : cashDate.trim();
        String activeTab = activeMovementTab(tab, selectedMovementDate, selectedCashDate);

        model.addAttribute("selectedMovementDate", selectedMovementDate);
        model.addAttribute("selectedCashDate", selectedCashDate);
        model.addAttribute("activeTab", activeTab);

        if ("true".equalsIgnoreCase(movementFilter) && selectedMovementDate.isBlank()) {
            model.addAttribute("movementDateError", "Elegi una fecha para filtrar movimientos.");
        }

        if ("true".equalsIgnoreCase(cashFilter) && selectedCashDate.isBlank()) {
            model.addAttribute("cashDateError", "Elegi una fecha para filtrar caja.");
        }

        if (model.containsAttribute("movementDateError") || model.containsAttribute("cashDateError")) {
            model.addAttribute("movements", List.of());
            model.addAttribute("movementGroups", List.of());
            model.addAttribute("movementCount", 0);
            model.addAttribute("movementCountLabel", "Movimientos hoy");
            model.addAttribute("cashEntries", List.of());
            model.addAttribute("cashGroups", List.of());
            model.addAttribute("cashTodayTotal", "0");
            model.addAttribute("cashSelectedTotal", "0");
            model.addAttribute("cashReportMonths", List.of());
            return "movements";
        }

        List<InventoryMovement> allMovements = List.of();

        try {
            allMovements = consolidateDailyMovements(inventoryService.getRecentMovements());
            var movements = allMovements;
            String countDate = selectedMovementDate.isBlank()
                    ? LocalDate.now(APP_ZONE).format(MOVEMENT_DATE_FORMAT)
                    : selectedMovementDate;

            if (!selectedMovementDate.isBlank()) {
                movements = movements.stream()
                        .filter(movement -> selectedMovementDate.equals(movement.getDate()))
                        .toList();

                if (movements.isEmpty()) {
                    model.addAttribute("error", "No hay movimientos registrados para esa fecha.");
                }
            }

            model.addAttribute("movements", movements);
            model.addAttribute("movementGroups", groupMovementsByMonth(movements, selectedMovementDate));
            model.addAttribute("movementCount", countMovementsForDate(
                    selectedMovementDate.isBlank() ? allMovements : movements,
                    countDate
            ));
            model.addAttribute(
                    "movementCountLabel",
                    selectedMovementDate.isBlank() ? "Movimientos hoy" : "Movimientos del dia"
            );
        } catch (Exception e) {
            model.addAttribute("error", "No se pudieron cargar los movimientos.");
            model.addAttribute("movements", List.of());
            model.addAttribute("movementGroups", List.of());
            model.addAttribute("movementCount", 0);
            model.addAttribute("movementCountLabel", "movimientos hoy");
        }

        addCashRegisterModel(model, selectedCashDate, allMovements);
        return "movements";
    }

    private String activeMovementTab(String tab, String selectedMovementDate, String selectedCashDate) {
        if ("cash".equalsIgnoreCase(tab) || "report".equalsIgnoreCase(tab) || "movements".equalsIgnoreCase(tab)) {
            return tab.toLowerCase();
        }

        if (selectedCashDate != null && !selectedCashDate.isBlank()) {
            return "cash";
        }

        return "movements";
    }

    private List<MovementMonthGroup> groupMovementsByMonth(
            List<InventoryMovement> movements,
            String selectedDate
    ) {
        if (movements == null || movements.isEmpty()) {
            return List.of();
        }

        YearMonth openMonth = monthFromDate(selectedDate);

        if (openMonth == null) {
            openMonth = YearMonth.now(APP_ZONE);
        }

        Map<String, List<InventoryMovement>> movementsByMonth = new LinkedHashMap<>();

        for (InventoryMovement movement : movements) {
            String monthKey = monthKey(movement.getDate());
            movementsByMonth.computeIfAbsent(monthKey, key -> new ArrayList<>())
                    .add(movement);
        }

        List<MovementMonthGroup> groups = new ArrayList<>();
        String openMonthKey = openMonth.toString();
        boolean hasOpenMonth = movementsByMonth.containsKey(openMonthKey);

        var sortedEntries = movementsByMonth.entrySet()
                .stream()
                .sorted((first, second) -> compareMonthKeys(second.getKey(), first.getKey()))
                .toList();

        for (var entry : sortedEntries) {
            groups.add(new MovementMonthGroup(
                    entry.getKey(),
                    monthLabel(entry.getKey()),
                    entry.getValue().size(),
                    entry.getKey().equals(openMonthKey) || !hasOpenMonth && groups.isEmpty(),
                    entry.getValue()
            ));
        }

        return groups;
    }

    private List<InventoryMovement> consolidateDailyMovements(List<InventoryMovement> movements) {
        if (movements == null || movements.isEmpty()) {
            return List.of();
        }

        Map<String, MovementAccumulator> movementsByCardAndDate = new LinkedHashMap<>();

        for (int i = movements.size() - 1; i >= 0; i--) {
            InventoryMovement movement = movements.get(i);
            String key = dailyMovementKey(movement);

            movementsByCardAndDate.computeIfAbsent(key, unused -> new MovementAccumulator(movement))
                    .add(movement);
        }

        List<InventoryMovement> consolidated = new ArrayList<>();

        for (MovementAccumulator accumulator : movementsByCardAndDate.values()) {
            consolidated.add(accumulator.toMovement());
        }

        java.util.Collections.reverse(consolidated);
        return consolidated;
    }

    private String dailyMovementKey(InventoryMovement movement) {
        return String.join(
                "|",
                normalizeKeyPart(movement.getDate()),
                normalizeKeyPart(movement.getName()),
                normalizeKeyPart(movement.getSetName()),
                normalizeKeyPart(movement.getSetCode()),
                normalizeKeyPart(movement.getCollectorNumber()),
                normalizeKeyPart(movement.getPrinting()),
                movementDirection(movement)
        );
    }

    private String movementDirection(InventoryMovement movement) {
        int quantity = signedQuantity(movement);

        if (quantity < 0) {
            return "salida";
        }

        return "entrada";
    }

    private String normalizeKeyPart(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private int signedQuantity(InventoryMovement movement) {
        String quantity = movement.getQuantity();

        if (quantity == null || quantity.isBlank()) {
            return 0;
        }

        try {
            int parsedQuantity = Integer.parseInt(quantity.trim());
            if ("SALIDA".equalsIgnoreCase(movement.getType()) && parsedQuantity > 0) {
                return -parsedQuantity;
            }

            return parsedQuantity;
        } catch (NumberFormatException e) {
            return "SALIDA".equalsIgnoreCase(movement.getType()) ? -1 : 1;
        }
    }

    private String stockAction(int quantity) {
        if (quantity > 0) {
            return "Agregado al stock";
        }

        return "Unidad vendida";
    }

    private String formattedMovementQuantity(int quantity) {
        if (quantity > 0) {
            return "+" + quantity;
        }

        return String.valueOf(quantity);
    }

    private long countMovementsForDate(List<InventoryMovement> movements, String date) {
        if (movements == null || date == null || date.isBlank()) {
            return 0;
        }

        return movements.stream()
                .filter(movement -> date.equals(movement.getDate()))
                .count();
    }

    private int compareMonthKeys(String first, String second) {
        if ("sin-fecha".equals(first) && "sin-fecha".equals(second)) {
            return 0;
        }

        if ("sin-fecha".equals(first)) {
            return -1;
        }

        if ("sin-fecha".equals(second)) {
            return 1;
        }

        return YearMonth.parse(first).compareTo(YearMonth.parse(second));
    }

    private String monthKey(String date) {
        YearMonth month = monthFromDate(date);
        return month == null ? "sin-fecha" : month.toString();
    }

    private YearMonth monthFromDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }

        try {
            return YearMonth.from(LocalDate.parse(date.trim(), MOVEMENT_DATE_FORMAT));
        } catch (Exception e) {
            return null;
        }
    }

    private String monthLabel(String monthKey) {
        if ("sin-fecha".equals(monthKey)) {
            return "Sin fecha";
        }

        YearMonth month = YearMonth.parse(monthKey);
        String monthName = month.getMonth()
                .getDisplayName(TextStyle.FULL, new Locale("es", "AR"));

        return monthName.substring(0, 1).toUpperCase()
                + monthName.substring(1)
                + " "
                + month.getYear();
    }

    private void addCashRegisterModel(Model model, String selectedDate, List<InventoryMovement> allMovements) {
        String today = LocalDate.now(APP_ZONE).format(MOVEMENT_DATE_FORMAT);

        try {
            var allEntries = inventoryService.getCashRegisterEntries();
            var entries = selectedDate == null || selectedDate.isBlank()
                    ? allEntries
                    : allEntries.stream()
                    .filter(entry -> selectedDate.equals(entry.getDate()))
                    .toList();

            model.addAttribute("cashEntries", entries);
            model.addAttribute("cashGroups", groupCashEntriesByMonth(entries, selectedDate));
            model.addAttribute("cashTodayTotal", formatCashTotal(totalSalesForDate(allEntries, today)));
            model.addAttribute("cashSelectedTotal", formatCashTotal(totalSalesForDate(entries, selectedDate)));
            model.addAttribute("cashReportMonths", cashReportMonths(allEntries, allMovements));
        } catch (Exception e) {
            model.addAttribute("cashEntries", List.of());
            model.addAttribute("cashGroups", List.of());
            model.addAttribute("cashTodayTotal", "0");
            model.addAttribute("cashSelectedTotal", "0");
            model.addAttribute("cashReportMonths", List.of());
            model.addAttribute("cashError", "No se pudo cargar la caja.");
        }
    }

    private List<CashMonthGroup> groupCashEntriesByMonth(
            List<CashRegisterEntry> entries,
            String selectedDate
    ) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        YearMonth openMonth = monthFromDate(selectedDate);
        if (openMonth == null) {
            openMonth = YearMonth.now(APP_ZONE);
        }

        Map<String, Map<String, List<CashRegisterEntry>>> entriesByMonthAndDay = new LinkedHashMap<>();

        for (CashRegisterEntry entry : entries) {
            String monthKey = monthKey(entry.getDate());
            entriesByMonthAndDay.computeIfAbsent(monthKey, key -> new LinkedHashMap<>())
                    .computeIfAbsent(entry.getDate(), key -> new ArrayList<>())
                    .add(entry);
        }

        List<CashMonthGroup> groups = new ArrayList<>();
        String openMonthKey = openMonth.toString();
        boolean hasOpenMonth = entriesByMonthAndDay.containsKey(openMonthKey);

        var sortedMonths = entriesByMonthAndDay.entrySet()
                .stream()
                .sorted((first, second) -> compareMonthKeys(second.getKey(), first.getKey()))
                .toList();

        for (var monthEntry : sortedMonths) {
            List<CashDayGroup> days = monthEntry.getValue()
                    .entrySet()
                    .stream()
                    .sorted((first, second) -> second.getKey().compareTo(first.getKey()))
                    .map(dayEntry -> new CashDayGroup(
                            dayEntry.getKey(),
                            dayEntry.getValue(),
                            formatCashTotal(totalSalesForEntries(dayEntry.getValue())),
                            formatCashTotal(totalSalesForEntries(dayEntry.getValue())),
                            dayEntry.getKey().equals(LocalDate.now(APP_ZONE).format(MOVEMENT_DATE_FORMAT))
                    ))
                    .toList();

            groups.add(new CashMonthGroup(
                    monthEntry.getKey(),
                    monthLabel(monthEntry.getKey()),
                    days.size(),
                    monthEntry.getKey().equals(openMonthKey) || !hasOpenMonth && groups.isEmpty(),
                    days
            ));
        }

        return groups;
    }

    private double totalSalesForDate(List<CashRegisterEntry> entries, String date) {
        if (date == null || date.isBlank()) {
            return totalSalesForEntries(entries);
        }

        return totalSalesForEntries(entries.stream()
                .filter(entry -> date.equals(entry.getDate()))
                .toList());
    }

    private double totalSalesForEntries(List<CashRegisterEntry> entries) {
        if (entries == null) {
            return 0;
        }

        return entries.stream()
                .filter(CashRegisterEntry::affectsCash)
                .mapToDouble(CashRegisterEntry::signedTotal)
                .sum();
    }

    private double parseCashTotal(String value) {
        return Amounts.parse(value);
    }

    private String formatCashTotal(double value) {
        return NumberFormat
                .getIntegerInstance(ARGENTINA_LOCALE)
                .format(Math.round(value));
    }

    private static String formatLocalPrice(String value) {
        return formatPrice(value, 0);
    }

    private static String formatUsdPrice(String value) {
        return formatPrice(value, 2);
    }

    private static String formatPrice(String value, int fractionDigits) {
        Double parsedValue = parsePriceValue(value);
        if (parsedValue == null) {
            return "";
        }

        NumberFormat formatter = NumberFormat.getNumberInstance(ARGENTINA_LOCALE);
        formatter.setMinimumFractionDigits(fractionDigits);
        formatter.setMaximumFractionDigits(fractionDigits);
        return formatter.format(parsedValue);
    }

    private static Double parsePriceValue(String value) {
        return Amounts.parseOrNull(value);
    }

    private List<CashReportMonth> cashReportMonths(
            List<CashRegisterEntry> cashEntries,
            List<InventoryMovement> movements
    ) {
        Map<String, Map<String, CashReportCardAccumulator>> cardsByMonth = new LinkedHashMap<>();

        if (movements != null) {
            for (InventoryMovement movement : movements) {
                int quantity = signedQuantity(movement);

                if (quantity == 0) {
                    continue;
                }

                String monthKey = monthKey(movement.getDate());
                CashReportCardAccumulator cardReport = cardsByMonth
                        .computeIfAbsent(monthKey, key -> new LinkedHashMap<>())
                        .computeIfAbsent(movementReportKey(movement), key -> new CashReportCardAccumulator());

                // Lo vendido sale de la caja, no de los movimientos: un ajuste de stock no es una venta
                // y una venta anulada que devuelve stock no es un ingreso nuevo.
                if (quantity > 0 && isNewStock(movement.getSource())) {
                    cardReport.addEntry(quantity);
                }
            }
        }

        if (cashEntries != null) {
            for (CashRegisterEntry entry : cashEntries) {
                if (!entry.affectsCash()) {
                    continue;
                }

                CashReportCardAccumulator cardReport = cardsByMonth
                        .computeIfAbsent(monthKey(entry.getDate()), key -> new LinkedHashMap<>())
                        .computeIfAbsent(cashEntryReportKey(entry), key -> new CashReportCardAccumulator());
                cardReport.addSaleTotal(entry.signedTotal());
                cardReport.addSold(entry.signedQuantity());
            }
        }

        Map<String, CashReportAccumulator> reports = new LinkedHashMap<>();
        for (var monthEntry : cardsByMonth.entrySet()) {
            CashReportAccumulator report = new CashReportAccumulator();

            for (CashReportCardAccumulator cardReport : monthEntry.getValue().values()) {
                report.addCard(cardReport);
            }

            reports.put(monthEntry.getKey(), report);
        }

        double maxSales = reports.values().stream()
                .mapToDouble(report -> report.salesTotal)
                .max()
                .orElse(0);

        int maxMovementQuantity = reports.values().stream()
                .mapToInt(report -> Math.max(report.soldQuantity, report.enteredQuantity))
                .max()
                .orElse(0);

        return reports.entrySet()
                .stream()
                .sorted((first, second) -> compareMonthKeys(second.getKey(), first.getKey()))
                .map(entry -> entry.getValue().toReport(
                        entry.getKey(),
                        monthLabel(entry.getKey()),
                        maxSales,
                        maxMovementQuantity
                ))
                .toList();
    }

    /** Una venta anulada o una reserva liberada devuelven cartas que ya estaban: no son ingresos nuevos. */
    private static boolean isNewStock(String source) {
        String value = source == null ? "" : source.trim().toLowerCase(java.util.Locale.ROOT);
        return !value.startsWith(StockOperationsService.SOURCE_VOIDED.toLowerCase(java.util.Locale.ROOT))
                && !value.startsWith(StockOperationsService.SOURCE_RESERVATION_RELEASED.toLowerCase(java.util.Locale.ROOT));
    }

    private String cashEntryReportKey(CashRegisterEntry entry) {
        return reportCardKey(
                entry.getName(),
                entry.getSetName(),
                entry.getSetCode(),
                entry.getCollectorNumber(),
                entry.getPrinting()
        );
    }

    private String movementReportKey(InventoryMovement movement) {
        return reportCardKey(
                movement.getName(),
                movement.getSetName(),
                movement.getSetCode(),
                movement.getCollectorNumber(),
                movement.getPrinting()
        );
    }

    private String reportCardKey(
            String name,
            String setName,
            String setCode,
            String collectorNumber,
            String printing
    ) {
        return normalizeKeyPart(name)
                + "|"
                + normalizeKeyPart(setName)
                + "|"
                + normalizeKeyPart(setCode)
                + "|"
                + collectorNumber(collectorNumber)
                + "|"
                + normalizeKeyPart(printing);
    }

    private int parseQuantity(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            return Math.abs(Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String percent(double value, double max) {
        if (max <= 0) {
            return "0%";
        }

        return Math.max(8, Math.round(value / max * 100)) + "%";
    }

    @GetMapping("/importar-lista")
    public String importList(Model model) {
        addBaseModel(model, "");
        model.addAttribute("rawList", "");
        model.addAttribute("importResults", List.of());
        model.addAttribute("analyzed", false);
        model.addAttribute("totalCount", 0);
        model.addAttribute("readyCount", 0);
        return "import-list";
    }

    @PostMapping("/importar-lista/analizar")
    public String analyzeImportList(
            @RequestParam("rawList") String rawList,
            Model model
    ) {
        if (rawList == null || rawList.isBlank()) {
            return populateImportAnalysisModel(
                    rawList,
                    model,
                    "Agrega al menos una carta para analizar la lista."
            );
        }

        return populateImportAnalysisModel(rawList, model, null);
    }

    @PostMapping("/importar-lista/confirmar")
    public String confirmImportList(
            @RequestParam("rawList") String rawList,
            @RequestParam(name = "selected", required = false) List<String> selected,
            RedirectAttributes redirectAttributes,
            Model model
    ) {
        if (selected == null || selected.isEmpty()) {
            return populateImportAnalysisModel(
                    rawList,
                    model,
                    "Selecciona al menos una carta para agregar al stock."
            );
        }

        try {
            var inventoryCards = inventoryService.getInventoryCards();
            var priceList = cardKingdomApiService.getPriceList();
            Map<String, CardKingdomProduct> productsBySku = indexProductsBySku(
                    priceList == null ? List.of() : priceList.getData()
            );
            Map<String, InventoryCard> inventoryByProductKey = indexInventoryCardsByProductKey(inventoryCards);
            Map<Integer, Integer> quantitiesByRow = new HashMap<>();
            Map<Integer, InventoryCard> cardsToWrite = new HashMap<>();
            List<InventoryCard> cardsToAppend = new ArrayList<>();
            List<InventoryMovement> movements = new ArrayList<>();
            int added = 0;
            int updated = 0;

            for (String selection : selected) {
                String[] parts = selection.split("\\|", -1);

                if (parts.length != 2) {
                    continue;
                }

                CardKingdomProduct product = productsBySku.get(parts[0].toLowerCase());
                int quantity = parsePositiveQuantity(parts[1]);

                if (product == null || quantity <= 0) {
                    continue;
                }

                String productKey = productInventoryKey(product);
                InventoryCard existingCard = inventoryByProductKey.get(productKey);

                if (existingCard == null) {
                    InventoryCard newCard = createInventoryCard(product);
                    newCard.setQuantity(String.valueOf(quantity));
                    cardsToAppend.add(newCard);
                    movements.add(createMovement("ENTRADA", quantity, newCard, 0, quantity, "Importar lista"));
                    added++;
                    continue;
                }

                int previousQuantity = quantity(existingCard);
                int updatedQuantity = quantitiesByRow.getOrDefault(
                        existingCard.getRowIndex(),
                        previousQuantity
                ) + quantity;
                quantitiesByRow.put(existingCard.getRowIndex(), updatedQuantity);
                existingCard.setQuantity(String.valueOf(updatedQuantity));
                applyStockAction(existingCard);
                cardsToWrite.put(existingCard.getRowIndex(), existingCard);
                movements.add(createMovement("ENTRADA", quantity, existingCard, updatedQuantity - quantity, updatedQuantity, "Importar lista"));
                updated++;
            }

            int remaining = licenseService.remainingNewCards(distinctCards(inventoryCards));
            if (cardsToAppend.size() > remaining) {
                return populateImportAnalysisModel(
                        rawList,
                        model,
                        licenseService.limitMessage() + " Esta lista suma " + cardsToAppend.size()
                                + " cartas nuevas y te quedan " + remaining + "."
                );
            }

            inventoryService.updateQuantities(quantitiesByRow);
            inventoryService.updateInventoryRows(cardsToWrite);
            inventoryService.appendInventoryCards(cardsToAppend);
            inventoryService.appendMovements(movements);
            refreshLatestUpdatesFromInventory();

            redirectAttributes.addFlashAttribute(
                    "success",
                    added + " carta(s) nuevas agregadas y " + updated + " carta(s) existentes actualizadas."
            );
        } catch (Exception e) {
            log.warn("No se pudo importar la lista.", e);
            return populateImportAnalysisModel(rawList, model, "No se pudo importar la lista.");
        }

        return "redirect:/importar-lista";
    }

    private String populateImportAnalysisModel(String rawList, Model model, String error) {
        addBaseModel(model, "");
        model.addAttribute("rawList", rawList == null ? "" : rawList);
        model.addAttribute("analyzed", true);

        if (error != null && !error.isBlank()) {
            model.addAttribute("error", error);
        }

        try {
            var results = analyzeImportLines(rawList);
            model.addAttribute("importResults", results);
            model.addAttribute("totalCount", results.size());
            model.addAttribute("readyCount", results.stream()
                    .mapToLong(result -> result.selectable()
                            ? 1
                            : result.alternatives().size())
                    .sum());
        } catch (Exception e) {
            model.addAttribute("error", "No se pudo analizar la lista.");
            model.addAttribute("importResults", List.of());
            model.addAttribute("totalCount", 0);
            model.addAttribute("readyCount", 0);
        }

        return "import-list";
    }

    private Map<String, CardKingdomProduct> indexProductsBySku(List<CardKingdomProduct> products) {
        Map<String, CardKingdomProduct> productsBySku = new HashMap<>();

        if (products == null) {
            return productsBySku;
        }

        for (CardKingdomProduct product : products) {
            if (product.getSku() != null) {
                productsBySku.put(product.getSku().toLowerCase(), product);
            }
        }

        return productsBySku;
    }

    private Map<String, InventoryCard> indexInventoryCardsByProductKey(List<InventoryCard> inventoryCards) {
        Map<String, InventoryCard> inventoryByProductKey = new HashMap<>();

        for (InventoryCard card : inventoryCards) {
            String key = importInventoryKey(
                    card.getName(),
                    card.getSetName(),
                    card.getSetCode(),
                    collectorNumber(card.getCollectorNumber()),
                    card.isFoil()
            );
            inventoryByProductKey.putIfAbsent(key, card);
        }

        return inventoryByProductKey;
    }

    private String productInventoryKey(CardKingdomProduct product) {
        return importInventoryKey(
                product.getName(),
                product.getEdition(),
                setCode(product.getSku()),
                collectorNumber(product.getSku()),
                "true".equalsIgnoreCase(product.getFoil())
        );
    }

    private List<ImportResult> analyzeImportLines(String rawList) throws Exception {
        if (rawList == null || rawList.isBlank()) {
            return List.of();
        }

        List<ImportResult> results = new ArrayList<>();
        var inventoryCards = inventoryService.getInventoryCards();
        Map<String, int[]> inventoryIndex = indexInventoryForImport(inventoryCards);
        var priceList = cardKingdomApiService.getPriceList();

        if (priceList == null || priceList.getData() == null) {
            throw new IllegalStateException("No hay lista de precios de Card Kingdom.");
        }

        Map<String, List<CardKingdomProduct>> productsByName =
                indexProductsByNameOrVariation(priceList.getData());

        for (ParsedImportLine parsedLine : parseAndMergeImportLines(rawList)) {

            var products = searchImportedProducts(productsByName, parsedLine);

            if (products.isEmpty()) {
                List<CardKingdomProduct> otherVersions = importNameCandidates(parsedLine.name()).stream()
                        .flatMap(candidate -> productsByName.getOrDefault(candidate, List.of()).stream())
                        .distinct()
                        .toList();
                List<ImportOption> alternatives = createImportOptions(otherVersions, parsedLine, inventoryIndex);

                String status = parsedLine.duplicateCount() > 0
                        ? importStatus(parsedLine)
                        : alternatives.isEmpty() ? "NO ENCONTRADA" : "OTRA VERSION";

                results.add(new ImportResult(
                        parsedLine.originalLine(),
                        parsedLine.quantity(),
                        parsedLine.name(),
                        "",
                        "",
                        "",
                        "",
                        "",
                        0,
                        0,
                        status,
                        false,
                        alternatives
                ));
                continue;
            }

            for (CardKingdomProduct product : products) {
                ImportResult importResult = createImportResult(product, parsedLine, inventoryIndex);

                results.add(new ImportResult(
                        parsedLine.originalLine(),
                        parsedLine.quantity(),
                        importResult.name(),
                        importResult.edition(),
                        importResult.sku(),
                        importResult.variation(),
                        importResult.printing(),
                        importResult.nmPrice(),
                        importResult.stockQuantity(),
                        importResult.rowIndex(),
                        importStatus(parsedLine),
                        importResult.selectable(),
                        List.of()
                ));
            }
        }

        return results.stream()
                .sorted(Comparator.comparingInt(this::importResultPriority))
                .toList();
    }

    private int importResultPriority(ImportResult result) {
        return switch (result.status()) {
            case "OTRA VERSION" -> 0;
            case "CARTA DUPLICADA" -> 1;
            case "NO ENCONTRADA" -> 2;
            default -> 3;
        };
    }

    private List<ParsedImportLine> parseAndMergeImportLines(String rawList) {
        Map<String, ParsedImportLine> linesByKey = new LinkedHashMap<>();

        for (String line : rawList.split("\\R")) {
            ParsedImportLine parsedLine = parseImportLine(line);

            if (parsedLine == null) {
                continue;
            }

            String key = importLineKey(parsedLine);
            ParsedImportLine existingLine = linesByKey.get(key);

            if (existingLine == null) {
                linesByKey.put(key, parsedLine);
                continue;
            }

            linesByKey.put(key, new ParsedImportLine(
                    existingLine.originalLine(),
                    existingLine.quantity() + parsedLine.quantity(),
                    existingLine.name(),
                    existingLine.setCode(),
                    existingLine.collectorNumber(),
                    existingLine.foil(),
                    existingLine.duplicateCount() + 1
            ));
        }

        return new ArrayList<>(linesByKey.values());
    }

    private String importLineKey(ParsedImportLine line) {
        return normalizeImportedName(line.name())
                + "|"
                + line.setCode().toLowerCase()
                + "|"
                + line.collectorNumber()
                + "|"
                + (line.foil() == null ? "" : line.foil());
    }

    private String importStatus(ParsedImportLine line) {
        return line.duplicateCount() > 0 ? "CARTA DUPLICADA" : "ENCONTRADA";
    }

    private ImportResult createImportResult(
            CardKingdomProduct product,
            ParsedImportLine parsedLine,
            Map<String, int[]> inventoryIndex
    ) {
        boolean foil = "true".equalsIgnoreCase(product.getFoil());
        String key = importInventoryKey(
                product.getName(),
                product.getEdition(),
                setCode(product.getSku()),
                collectorNumber(product.getSku()),
                foil
        );

        int[] stockData = inventoryIndex.getOrDefault(key, new int[]{0, 0});
        String nmPrice = product.getConditionValues() == null
                ? ""
                : product.getConditionValues().getNmPrice();

        return new ImportResult(
                parsedLine.originalLine(),
                parsedLine.quantity(),
                product.getName(),
                product.getEdition(),
                product.getSku(),
                product.getVariation(),
                foil ? "Foil" : "No Foil",
                nmPrice,
                stockData[0],
                stockData[1],
                "ENCONTRADA",
                true,
                List.of()
        );
    }

    private List<ImportOption> createImportOptions(
            List<CardKingdomProduct> products,
            ParsedImportLine parsedLine,
            Map<String, int[]> inventoryIndex
    ) {
        return products.stream()
                .filter(product -> {
                    boolean requestedFoil = parsedLine.foil() != null && parsedLine.foil();
                    boolean productFoil = "true".equalsIgnoreCase(product.getFoil());
                    return requestedFoil == productFoil;
                })
                .map(product -> {
                    ImportResult importResult = createImportResult(product, parsedLine, inventoryIndex);
                    return new ImportOption(
                            parsedLine.quantity(),
                            importResult.name(),
                            importResult.edition(),
                            importResult.sku(),
                            importResult.variation(),
                            importResult.printing(),
                            importResult.nmPrice(),
                            importResult.stockQuantity()
                    );
                })
                .toList();
    }

    private Map<String, int[]> indexInventoryForImport(List<InventoryCard> inventoryCards) {
        Map<String, int[]> inventoryIndex = new HashMap<>();

        for (InventoryCard card : inventoryCards) {
            int quantity = quantity(card);

            if (quantity <= 0 || card.getName() == null || card.getName().isBlank()) {
                continue;
            }

            String key = importInventoryKey(
                    card.getName(),
                    card.getSetName(),
                    card.getSetCode(),
                    collectorNumber(card.getCollectorNumber()),
                    card.isFoil()
            );
            int[] stockData = inventoryIndex.computeIfAbsent(key, ignored -> new int[]{0, 0});
            stockData[0] += quantity;

            if (stockData[1] <= 0 && card.getRowIndex() > 0) {
                stockData[1] = card.getRowIndex();
            }
        }

        return inventoryIndex;
    }

    private String importInventoryKey(
            String name,
            String setName,
            String setCode,
            String collectorNumber,
            boolean foil
    ) {
        return normalizeImportedName(name)
                + "|"
                + normalizeImportedName(setName)
                + "|"
                + (setCode == null ? "" : setCode.toLowerCase())
                + "|"
                + collectorNumber
                + "|"
                + foil;
    }

    private Map<String, List<CardKingdomProduct>> indexProductsByNameOrVariation(
            List<CardKingdomProduct> products
    ) {
        Map<String, List<CardKingdomProduct>> productsByName = new HashMap<>();

        for (CardKingdomProduct product : products) {
            indexImportedProduct(productsByName, product, product.getName());
            indexImportedProduct(productsByName, product, product.getVariation());
        }

        return productsByName;
    }

    private void indexImportedProduct(
            Map<String, List<CardKingdomProduct>> productsByName,
            CardKingdomProduct product,
            String name
    ) {
        for (String candidate : importNameCandidates(name)) {
            if (candidate.isBlank()) {
                continue;
            }

            List<CardKingdomProduct> indexedProducts =
                    productsByName.computeIfAbsent(candidate, ignored -> new ArrayList<>());

            if (!indexedProducts.contains(product)) {
                indexedProducts.add(product);
            }
        }
    }

    private List<CardKingdomProduct> searchImportedProducts(
            Map<String, List<CardKingdomProduct>> productsByName,
            ParsedImportLine line
    ) {
        List<CardKingdomProduct> products = importNameCandidates(line.name()).stream()
                .flatMap(candidate -> productsByName.getOrDefault(candidate, List.of()).stream())
                .distinct()
                .toList();

        if (products == null || products.isEmpty()) {
            return List.of();
        }

        return products.stream()
                .filter(product -> {
                    if (!line.setCode().isBlank()
                            && (product.getSku() == null
                            || !product.getSku().toLowerCase()
                            .startsWith(line.setCode().toLowerCase() + "-"))) {
                        return false;
                    }

                    if (!line.collectorNumber().isBlank()
                            && !importCollectorFromSku(product.getSku()).equals(line.collectorNumber())) {
                        return false;
                    }

                    boolean requestedFoil = line.foil() != null && line.foil();
                    boolean productFoil = "true".equalsIgnoreCase(product.getFoil());

                    if (requestedFoil != productFoil) {
                        return false;
                    }

                    return true;
                })
                .toList();
    }

    private List<String> importNameCandidates(String name) {
        String normalizedName = normalizeImportedName(name);
        List<String> candidates = new ArrayList<>();

        if (!normalizedName.isBlank()) {
            candidates.add(normalizedName);
        }

        if (name != null && name.contains("/")) {
            for (String faceName : name.split("/")) {
                String normalizedFace = normalizeImportedName(faceName);

                if (!normalizedFace.isBlank() && !candidates.contains(normalizedFace)) {
                    candidates.add(normalizedFace);
                }
            }
        }

        return candidates;
    }

    private ParsedImportLine parseImportLine(String line) {
        if (line == null) {
            return null;
        }

        String normalizedLine = line.trim();
        if (normalizedLine.isBlank()
                || normalizedLine.startsWith("//")
                || normalizedLine.startsWith("#")) {
            return null;
        }

        int quantity = 1;
        String namePart = normalizedLine;

        Matcher leadingQuantity = LEADING_QUANTITY_PATTERN.matcher(namePart);
        Matcher trailingQuantity = TRAILING_QUANTITY_PATTERN.matcher(namePart);

        if (leadingQuantity.matches()) {
            quantity = Integer.parseInt(leadingQuantity.group(1));
            namePart = leadingQuantity.group(2).trim();
        } else if (trailingQuantity.matches()) {
            namePart = trailingQuantity.group(1).trim();
            quantity = Integer.parseInt(trailingQuantity.group(2));
        }

        String setCode = "";
        Matcher setCodeMatcher = SET_CODE_PATTERN.matcher(namePart);
        if (setCodeMatcher.find()) {
            setCode = setCodeMatcher.group(1);
            namePart = setCodeMatcher.replaceAll("").trim();
        }

        String collector = "";
        Matcher collectorMatcher = COLLECTOR_PATTERN.matcher(namePart);
        if (collectorMatcher.find() && isCollectorToken(namePart, collectorMatcher)) {
            collector = collectorMatcher.group(1);
            namePart = namePart.substring(0, collectorMatcher.start()).trim();
        }

        String lowercaseNamePart = namePart.toLowerCase();
        Boolean foil = null;
        if (lowercaseNamePart.contains("nonfoil") || lowercaseNamePart.contains("non foil")) {
            foil = false;
        } else if (lowercaseNamePart.contains("foil")) {
            foil = true;
        }
        namePart = namePart
                .replaceAll("(?i)\\bnon[- ]?foil\\b", "")
                .replaceAll("(?i)\\bfoil\\b", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (namePart.isBlank() || quantity <= 0) {
            return null;
        }

        return new ParsedImportLine(
                normalizedLine,
                quantity,
                namePart,
                setCode,
                normalizeImportCollector(collector),
                foil,
                0
        );
    }

    private boolean isCollectorToken(String line, Matcher collectorMatcher) {
        String collector = collectorMatcher.group(1);

        if (collector != null && collector.matches(".*\\d.*")) {
            return true;
        }

        int tokenStart = collectorMatcher.start(1);
        return tokenStart > 0 && line.charAt(tokenStart - 1) == '#';
    }

    private String importCollectorFromSku(String sku) {
        if (sku == null) {
            return "";
        }

        String collector = sku.contains("-")
                ? sku.substring(sku.indexOf("-") + 1)
                : sku;

        return normalizeImportCollector(collector);
    }

    private String normalizeImportCollector(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .replaceFirst("^0+(?!$)", "");
    }

    private String normalizeImportedName(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase()
                .replace("//", "/")
                .replaceAll("\\s*/\\s*", " / ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private int parsePositiveQuantity(String value) {
        try {
            return Math.max(Integer.parseInt(value), 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @PostMapping(value = "/configuracion", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object updateStoreSettings(
            @RequestParam("storeName") String storeName,
            @RequestParam(name = "spreadsheetId", required = false) String spreadsheetId,
            @RequestParam("inventorySheetName") String inventorySheetName,
            @RequestParam(name = "cacheDirectory", required = false) String cacheDirectory,
            @RequestParam("ckDollarRate") double ckDollarRate,
            @RequestParam("roundMultiple") int roundMultiple,
            @RequestParam(name = "markupPercent", required = false) Double markupPercent,
            @RequestParam(name = "minimumPrice", required = false) Double minimumPrice,
            @RequestParam(name = "sourcesSubmitted", defaultValue = "false") boolean sourcesSubmitted,
            @RequestParam(name = "tcgplayerEnabled", defaultValue = "false") boolean tcgplayerEnabled,
            @RequestParam(name = "cardmarketEnabled", defaultValue = "false") boolean cardmarketEnabled,
            @RequestParam(name = "eurRate", required = false) Double eurRate,
            @RequestParam(name = "combineMode", required = false) String combineMode,
            @RequestParam(name = "storeLogo", required = false) MultipartFile storeLogo,
            @RequestParam(name = "googleCredentials", required = false) MultipartFile googleCredentials,
            @RequestParam(defaultValue = "false") boolean removeLogo,
            @RequestHeader(name = "X-Requested-With", required = false) String requestedWith,
            RedirectAttributes redirectAttributes
    ) {
        boolean asyncRequest = "fetch".equalsIgnoreCase(requestedWith);

        try {
            storeSettingsService.validateSettings(storeName, spreadsheetId, inventorySheetName, cacheDirectory);
            storeSettingsService.validateLogo(storeLogo);
            storeSettingsService.validateGoogleCredentials(googleCredentials);
            pricingSettingsService.update(
                    ckDollarRate,
                    roundMultiple,
                    markupPercent == null ? pricingSettingsService.getMarkupPercent() : markupPercent,
                    minimumPrice == null ? pricingSettingsService.getMinimumPrice() : minimumPrice
            );
            if (sourcesSubmitted) {
                pricingSettingsService.updateSources(
                        tcgplayerEnabled,
                        cardmarketEnabled,
                        eurRate == null ? 0 : eurRate,
                        com.tcg.bot.service.PricingSettingsService.CombineMode.from(combineMode)
                );
            }
            storeSettingsService.update(storeName, spreadsheetId, inventorySheetName, cacheDirectory);

            if (removeLogo) {
                storeSettingsService.removeLogo();
            }
            storeSettingsService.saveLogo(storeLogo);
            storeSettingsService.saveGoogleCredentials(googleCredentials);
            inventoryService.clearServiceAccountEmailCache();

            try {
                if (synchronizeInventory(false)) {
                    return configurationResponse(
                            asyncRequest,
                            redirectAttributes,
                            true,
                            "Configuracion guardada e inventario sincronizado.",
                            HttpStatus.OK
                    );
                } else {
                    return configurationResponse(
                            asyncRequest,
                            redirectAttributes,
                            false,
                            "Configuracion guardada, pero no se pudo sincronizar Card Kingdom en este momento.",
                            HttpStatus.SERVICE_UNAVAILABLE
                    );
                }
            } catch (Exception syncException) {
                log.warn("Configuracion guardada, pero no se pudo sincronizar.", syncException);
                return configurationResponse(
                        asyncRequest,
                        redirectAttributes,
                        false,
                        "Configuracion guardada, pero no se pudo sincronizar: " + syncErrorMessage(syncException),
                        HttpStatus.INTERNAL_SERVER_ERROR
                );
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("submittedInventorySheetName", inventorySheetName);
            if (e.getMessage().toLowerCase().contains("hoja")) {
                redirectAttributes.addFlashAttribute("inventorySheetError", e.getMessage());
                return configurationResponse(
                        asyncRequest,
                        redirectAttributes,
                        false,
                        e.getMessage(),
                        HttpStatus.BAD_REQUEST
                );
            }
            return configurationResponse(
                    asyncRequest,
                    redirectAttributes,
                    false,
                    e.getMessage(),
                    HttpStatus.BAD_REQUEST
            );
        } catch (Exception e) {
            log.warn("No se pudo guardar la configuracion de la tienda.", e);
            return configurationResponse(
                    asyncRequest,
                    redirectAttributes,
                    false,
                    "No se pudo guardar la configuracion de la tienda: " + syncErrorMessage(e),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private Object configurationResponse(
            boolean asyncRequest,
            RedirectAttributes redirectAttributes,
            boolean success,
            String message,
            HttpStatus status
    ) {
        if (asyncRequest) {
            return ResponseEntity
                    .status(status)
                    .body(Map.of(
                            "success", success,
                            "message", message
                    ));
        }

        redirectAttributes.addFlashAttribute(success ? "success" : "error", message);
        return "redirect:/configuracion";
    }
    @GetMapping("/configuracion/logo")
    public ResponseEntity<Resource> storeLogo() {
        return storeSettingsService.getLogoPath()
                .map(path -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(storeSettingsService.getLogoContentType()))
                        .body((Resource) new FileSystemResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/configuracion/precio")
    public String updatePricingRule(
            @RequestParam("ckDollarRate") double ckDollarRate,
            @RequestParam("roundMultiple") int roundMultiple,
            @RequestParam(name = "markupPercent", required = false) Double markupPercent,
            @RequestParam(name = "minimumPrice", required = false) Double minimumPrice,
            Model model
    ) {
        try {
            pricingSettingsService.update(
                    ckDollarRate,
                    roundMultiple,
                    markupPercent == null ? pricingSettingsService.getMarkupPercent() : markupPercent,
                    minimumPrice == null ? pricingSettingsService.getMinimumPrice() : minimumPrice
            );
            if (synchronizeInventory(false)) {
                model.addAttribute("success", "Regla de precio guardada e inventario sincronizado.");
            } else {
                model.addAttribute("success", "Regla de precio guardada. No se pudo sincronizar Card Kingdom en este momento.");
            }
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
        } catch (Exception e) {
            model.addAttribute("error", "No se pudo guardar o sincronizar la regla de precio.");
        }

        addBaseModel(model, "");
        addLatestUpdates(model);

        return "dashboard";
    }

    @PostMapping("/actualizar")
    public String updateInventory(Model model) {
        addBaseModel(model, "");
        addLatestUpdates(model);

        try {
            ensureInventorySetup();
            if (!synchronizeInventory(true)) {
                model.addAttribute("error", "No se pudo obtener la lista actualizada de Card Kingdom.");
                return "dashboard";
            }
            addLatestUpdates(model);
        } catch (IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo actualizar el inventario.", e);
            model.addAttribute("error", "No se pudo actualizar el inventario: " + syncErrorMessage(e));
        }

        return "dashboard";
    }

    private String syncErrorMessage(Exception exception) {
        Throwable root = rootCause(exception);

        if (root instanceof com.google.api.client.googleapis.json.GoogleJsonResponseException googleException) {
            int statusCode = googleException.getStatusCode();
            String details = googleException.getDetails() == null
                    ? ""
                    : googleException.getDetails().getMessage();

            if ((statusCode == 403 || statusCode == 404) && inventoryService.usesGoogleAccount()) {
                return "Con la cuenta de Google conectada, la app solo puede abrir los Sheets que creó ella. "
                        + "Tocá \"Crear mi Sheet de inventario\" en Configuración, o desconectá la cuenta para usar tu Sheet compartido con el bot.";
            }

            if (statusCode == 403) {
                return "Google rechazo el acceso. Revisa que el Sheet este compartido como Editor con el mail de Configuracion y que Google Sheets API este habilitada para esas credenciales.";
            }

            if (statusCode == 404) {
                return "Google no encontro el Sheet. Revisa que el ID o enlace sea correcto y que el mail editor tenga acceso.";
            }

            if (statusCode == 400 && details != null && details.toLowerCase().contains("unable to parse range")) {
                return "Google no pudo leer la pestaña configurada. Revisa el nombre de la pestaña en Configuracion.";
            }

            if (details != null && !details.isBlank()) {
                return "Google Sheets respondio " + statusCode + ": " + details;
            }

            return "Google Sheets respondio " + statusCode + ".";
        }

        if (root instanceof java.net.UnknownHostException) {
            return "no hay conexion a internet o no se pudo resolver el servidor externo.";
        }

        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return "revisa permisos del Sheet, credenciales y conexion a Card Kingdom.";
        }

        return message;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }

        return current;
    }

    private boolean synchronizeInventory(boolean refreshPriceList) throws Exception {
        ensureInventorySetup();
        var priceList = cardKingdomApiService.getPriceList(refreshPriceList);

        if (priceList == null || priceList.getData() == null) {
            return false;
        }

        inventoryService.prepareInventorySheet(priceList.getData());
        inventoryService.sortInventoryByName();
        var cards = inventoryService.getInventoryCards();

        List<UpdateResult> updates = new ArrayList<>();
        Map<Integer, InventoryCard> cardsToWrite = new HashMap<>();

        Map<String, ScryfallPriceService.Prices> extraPrices = pricingSettingsService.usesExtraSources()
                ? scryfallPriceService.prices(cards.stream()
                        .filter(card -> quantity(card) > 0)
                        .map(card -> new ScryfallPriceService.CardRef(card.getSetCode(), card.getCollectorNumber()))
                        .toList())
                : Map.of();

        for (var card : cards) {
            UpdateResult update = updateCard(card.getRowIndex(), card, priceList, extraPrices);
            updates.add(update);

            if (card.getRowIndex() > 0 && update.writeRequired()) {
                cardsToWrite.put(card.getRowIndex(), card);
            }
        }

        inventoryService.updateInventoryRows(cardsToWrite);
        priceHistoryService.recordQuietly(cards, extraPrices);

        latestUpdates = List.copyOf(updates);
        latestUpdatedCount = updates.stream()
                .filter(UpdateResult::writeRequired)
                .count();

        return true;
    }

    private void ensureInventorySetup() {
        if (!storeSettingsService.hasSpreadsheetConfigured()) {
            throw new IllegalStateException(
                    "Configura primero el Google Sheet desde Configuracion. Copia su enlace o ID y guarda los cambios."
            );
        }

        if (!inventoryService.hasCredentialsConfigured()) {
            throw new IllegalStateException(
                    "Falta google-credentials.json. Copialo en " + inventoryService.getConfiguredCredentialsPath()
                            + " o configura GOOGLE_APPLICATION_CREDENTIALS."
            );
        }
    }

    private UpdateResult updateCard(
            int rowIndex,
            InventoryCard card,
            com.tcg.bot.dto.CardKingdomPriceListResponse priceList,
            Map<String, ScryfallPriceService.Prices> extraPrices
    ) throws Exception {
        String previousCkPrice = card.getCkPriceUsd();
        String previousLocalPrice = card.getLocalPrice();
        String previousAction = card.getAction();

        if (quantity(card) <= 0) {
            card.setAction(ACTION_OUT_OF_STOCK);

            return new UpdateResult(
                    card.getName(),
                    card.getSetName(),
                    card.getSetCode(),
                    card.getCollectorNumber(),
                    card.getLocalPrice(),
                    card.getCkPriceUsd(),
                    card.getAction(),
                    rowIndex,
                    quantity(card),
                    !Objects.equals(previousAction, card.getAction())
            );
        }

        var product = cardKingdomApiService.findProduct(card, priceList);

        if (product == null) {
            card.setCkPriceUsd("");
            card.setLocalPrice("");
            card.setAction(ACTION_NO_CK_MATCH);

            return new UpdateResult(
                    card.getName(),
                    card.getSetName(),
                    card.getSetCode(),
                    card.getCollectorNumber(),
                    card.getLocalPrice(),
                    card.getCkPriceUsd(),
                    card.getAction(),
                    rowIndex,
                    quantity(card),
                    changed(previousCkPrice, previousLocalPrice, previousAction, card)
            );
        }

        Double ckPrice = priceComparisonService.getBestConditionPrice(product, card);

        if (ckPrice == null) {
            card.setCkPriceUsd("");
            card.setLocalPrice("");
            card.setAction(ACTION_NO_CK_PRICE);

            return new UpdateResult(
                    card.getName(),
                    card.getSetName(),
                    card.getSetCode(),
                    card.getCollectorNumber(),
                    card.getLocalPrice(),
                    "",
                    card.getAction(),
                    rowIndex,
                    quantity(card),
                    changed(previousCkPrice, previousLocalPrice, previousAction, card)
            );
        }

        var quote = priceComparisonService.quote(
                ckPrice,
                extraPrices.get(ScryfallPriceService.key(card.getSetCode(), card.getCollectorNumber())),
                card.isFoil()
        );
        double suggested = quote.localPrice() == null
                ? priceComparisonService.calculateLocalPrice(ckPrice)
                : quote.localPrice();
        String localPrice = String.format("%.0f", suggested);
        card.setCkPriceUsd(String.format(java.util.Locale.US, "%.2f", ckPrice));
        card.setLocalPrice(localPrice);
        card.setAction(ACTION_IN_STOCK);

        if (!changed(previousCkPrice, previousLocalPrice, previousAction, card)) {
            return new UpdateResult(
                    card.getName(),
                    card.getSetName(),
                    card.getSetCode(),
                    card.getCollectorNumber(),
                    card.getLocalPrice(),
                    card.getCkPriceUsd(),
                    card.getAction(),
                    rowIndex,
                    quantity(card),
                    false
            );
        }

        return new UpdateResult(
                card.getName(),
                card.getSetName(),
                card.getSetCode(),
                card.getCollectorNumber(),
                card.getLocalPrice(),
                card.getCkPriceUsd(),
                card.getAction(),
                rowIndex,
                quantity(card),
                true
        );
    }

    private boolean changed(
            String previousCkPrice,
            String previousLocalPrice,
            String previousAction,
            InventoryCard card
    ) {
        return !Objects.equals(normalizePrice(previousCkPrice), normalizePrice(card.getCkPriceUsd()))
                || !Objects.equals(normalizePrice(previousLocalPrice), normalizePrice(card.getLocalPrice()))
                || !Objects.equals(previousAction, card.getAction());
    }

    private String normalizePrice(String value) {
        return value == null ? null : value.replace(",", ".");
    }

    private SearchResult createSearchResult(
            CardKingdomProduct product,
            List<InventoryCard> inventoryCards,
            Map<String, ScryfallPriceService.Prices> extraPrices
    ) {
        String collectorNumber = collectorNumber(product.getSku());
        boolean foil = "true".equalsIgnoreCase(product.getFoil());
        var matches = inventoryCards.stream()
                .filter(card -> matchesInventoryCard(card, product, collectorNumber, foil))
                .toList();

        int stockQuantity = matches.stream()
                .mapToInt(this::quantity)
                .sum();

        // Si hay varias filas de la misma impresion (por ejemplo NM y LP), el "-" descuenta de una que tenga stock.
        InventoryCard target = matches.stream()
                .filter(card -> card.getRowIndex() > 0 && quantity(card) > 0)
                .findFirst()
                .orElse(matches.stream()
                        .filter(card -> card.getRowIndex() > 0)
                        .findFirst()
                        .orElse(null));
        int rowIndex = target == null ? 0 : target.getRowIndex();
        String cardId = target == null || target.getId() == null ? "" : target.getId();

        String nmPrice = product.getConditionValues() == null
                ? ""
                : product.getConditionValues().getNmPrice();

        return new SearchResult(
                product.getName(),
                product.getEdition(),
                product.getSku(),
                product.getVariation(),
                foil ? "Foil" : "No Foil",
                nmPrice,
                stockQuantity,
                rowIndex,
                cardId,
                comparePrices(product, foil, extraPrices),
                target == null ? 0 : com.tcg.bot.util.Amounts.parse(target.getLocalPrice())
        );
    }

    private PriceComparison comparePrices(
            CardKingdomProduct product,
            boolean foil,
            Map<String, ScryfallPriceService.Prices> extraPrices
    ) {
        Double ckUsd = priceComparisonService.extractConditionPrice(product, "NM");
        var quote = priceComparisonService.quote(
                ckUsd,
                extraPrices.get(ScryfallPriceService.key(setCode(product.getSku()), collectorNumber(product.getSku()))),
                foil
        );
        var tcg = quote.find(com.tcg.bot.service.PriceComparisonService.TCGPLAYER);
        var cm = quote.find(com.tcg.bot.service.PriceComparisonService.CARDMARKET);
        return new PriceComparison(
                tcg == null ? "" : "US$ " + formatPrice(String.valueOf(tcg.price()), 2),
                cm == null ? "" : "€ " + formatPrice(String.valueOf(cm.price()), 2),
                quote.localPrice() == null ? "" : "$ " + formatPrice(String.valueOf(quote.localPrice()), 0),
                quote.localPrice() == null ? 0 : quote.localPrice()
        );
    }

    public record PriceComparison(String tcgplayer, String cardmarket, String suggestedLocal, double suggestedValue) {
    }

    private boolean matchesInventoryCard(
            InventoryCard card,
            CardKingdomProduct product,
            String collectorNumber,
            boolean foil
    ) {
        if (card.getName() == null || !card.getName().equalsIgnoreCase(product.getName())) {
            return false;
        }

        if (card.getSetName() == null
                || product.getEdition() == null
                || !product.getEdition().equalsIgnoreCase(card.getSetName())) {
            return false;
        }

        if (card.getSetCode() == null
                || card.getSetCode().isBlank()
                || product.getSku() == null
                || !product.getSku().toLowerCase()
                .startsWith(card.getSetCode().toLowerCase() + "-")) {
            return false;
        }

        return collectorNumber(card.getCollectorNumber()).equals(collectorNumber)
                && card.isFoil() == foil;
    }

    private String collectorNumber(String value) {
        if (value == null) {
            return "";
        }

        String suffix = value.contains("-")
                ? value.substring(value.lastIndexOf("-") + 1)
                : value;

        return suffix.replaceAll("[^0-9]", "")
                .replaceFirst("^0+(?!$)", "");
    }

    private int quantity(InventoryCard card) {
        try {
            int quantity = com.tcg.bot.util.Amounts.parseInt(card.getQuantity());
            return Math.max(quantity, 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int distinctCards(List<InventoryCard> cards) {
        return (int) cards.stream()
                .filter(card -> card.getName() != null && !card.getName().isBlank())
                .count();
    }

    @PostMapping("/configuracion/licencia")
    public String updateLicense(@RequestParam(name = "licenseKey", required = false) String licenseKey,
                                RedirectAttributes redirectAttributes) {
        try {
            var status = licenseService.activate(licenseKey);
            redirectAttributes.addFlashAttribute(status.pro() ? "licenseSuccess" : "licenseError", status.message());
        } catch (Exception e) {
            log.warn("No se pudo guardar la licencia.", e);
            redirectAttributes.addFlashAttribute("licenseError", "No se pudo guardar la licencia: " + e.getMessage());
        }
        return "redirect:/configuracion";
    }

    private InventoryCard findInventoryCardByRow(int rowIndex) throws Exception {
        return inventoryService.getInventoryCards()
                .stream()
                .filter(card -> card.getRowIndex() == rowIndex)
                .findFirst()
                .orElse(null);
    }

    private InventoryMovement createMovement(
            String type,
            int quantity,
            InventoryCard card,
            int previousStock,
            int newStock,
            String source
    ) {
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        String movementQuantity = "SALIDA".equalsIgnoreCase(type)
                ? "-" + quantity
                : String.valueOf(quantity);
        String movementAction = stockAction("SALIDA".equalsIgnoreCase(type) ? -quantity : quantity);

        return new InventoryMovement(
                now.format(MOVEMENT_DATE_TIME_FORMAT),
                now.format(MOVEMENT_DATE_FORMAT),
                now.format(MOVEMENT_TIME_FORMAT),
                type,
                movementQuantity,
                card.getName() == null ? "" : card.getName(),
                card.getSetName() == null ? "" : card.getSetName(),
                card.getSetCode() == null ? "" : card.getSetCode(),
                card.getCollectorNumber() == null ? "" : card.getCollectorNumber(),
                card.getPrinting() == null ? "" : card.getPrinting(),
                String.valueOf(previousStock),
                String.valueOf(newStock),
                movementAction
        );
    }

    private void addBaseModel(Model model, String query) {
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("updates", List.of());
        model.addAttribute("updatePerformed", false);
        model.addAttribute("updatedCount", 0);
        model.addAttribute("ckDollarRate", pricingSettingsService.getCkDollarRate());
        model.addAttribute("roundMultiple", pricingSettingsService.getRoundMultiple());
        model.addAttribute("markupPercent", pricingSettingsService.getMarkupPercent());
        model.addAttribute("license", licenseService.status());
        model.addAttribute("licensePurchaseUrl", licenseService.getPurchaseUrl());
        model.addAttribute("freeCardLimit", LicenseService.FREE_CARD_LIMIT);
        model.addAttribute("minimumPrice", pricingSettingsService.getMinimumPrice());
        model.addAttribute("tcgplayerEnabled", pricingSettingsService.isTcgplayerEnabled());
        model.addAttribute("cardmarketEnabled", pricingSettingsService.isCardmarketEnabled());
        model.addAttribute("eurRate", pricingSettingsService.getEurRate());
        model.addAttribute("combineMode", pricingSettingsService.getCombineMode().name());
        model.addAttribute("combineModes", com.tcg.bot.service.PricingSettingsService.CombineMode.values());
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("hasStoreLogo", storeSettingsService.hasLogo());
        model.addAttribute("sheetEditorEmail", inventoryService.getServiceAccountEmail());
        model.addAttribute("sheetConfigured", storeSettingsService.hasSpreadsheetConfigured());
        model.addAttribute("credentialsConfigured", inventoryService.hasCredentialsConfigured());
        model.addAttribute("credentialsPath", inventoryService.getConfiguredCredentialsPath());
        model.addAttribute("localCredentialsConfigured", storeSettingsService.hasGoogleCredentials());
    }

    private void addStoreModel(Model model) {
        model.addAttribute("spreadsheetId", storeSettingsService.getSpreadsheetId());
        model.addAttribute("cacheDirectory", storeSettingsService.getCacheDirectory());
        if (!model.containsAttribute("inventorySource")) {
            model.addAttribute("inventorySource", storeSettingsService.getInventorySource());
        }
        if (!model.containsAttribute("submittedInventorySheetName")) {
            model.addAttribute("submittedInventorySheetName", storeSettingsService.getInventorySheetName());
        }
    }

    private void addLatestUpdates(Model model) {
        model.addAttribute("updates", latestUpdates);
        model.addAttribute("updatePerformed", !latestUpdates.isEmpty());
        model.addAttribute("updatedCount", latestUpdatedCount);

        if (latestUpdates.isEmpty()) {
            return;
        }
    }

    private void refreshLatestUpdatesFromInventory() throws Exception {
        if (latestUpdates.isEmpty()) {
            return;
        }

        Map<Integer, InventoryCard> cardsByRow = new HashMap<>();

        for (InventoryCard card : inventoryService.getInventoryCards()) {
            if (card.getRowIndex() > 0) {
                cardsByRow.put(card.getRowIndex(), card);
            }
        }

        List<UpdateResult> refreshed = new ArrayList<>();
        Map<Integer, Boolean> includedRows = new HashMap<>();

        for (UpdateResult update : latestUpdates) {
            InventoryCard card = cardsByRow.get(update.rowIndex());

            if (card == null) {
                refreshed.add(update);
                continue;
            }

            refreshed.add(updateResultFromCard(card));
            includedRows.put(card.getRowIndex(), true);
        }

        for (InventoryCard card : cardsByRow.values()) {
            if (!includedRows.containsKey(card.getRowIndex())) {
                refreshed.add(updateResultFromCard(card));
            }
        }

        latestUpdates = List.copyOf(refreshed);
    }

    private void refreshLatestUpdateForCard(InventoryCard card) {
        if (latestUpdates.isEmpty() || card == null || card.getRowIndex() <= 0) {
            return;
        }

        List<UpdateResult> refreshed = new ArrayList<>();
        boolean replaced = false;

        for (UpdateResult update : latestUpdates) {
            if (update.rowIndex() == card.getRowIndex()) {
                refreshed.add(updateResultFromCard(card));
                replaced = true;
            } else {
                refreshed.add(update);
            }
        }

        if (!replaced) {
            refreshed.add(updateResultFromCard(card));
        }

        latestUpdates = List.copyOf(refreshed);
    }

    private UpdateResult updateResultFromCard(InventoryCard card) {
        return new UpdateResult(
                card.getName(),
                card.getSetName(),
                card.getSetCode(),
                card.getCollectorNumber(),
                card.getLocalPrice(),
                card.getCkPriceUsd(),
                stockActionForQuantity(quantity(card)),
                card.getRowIndex(),
                quantity(card),
                false
        );
    }

    private void applyStockAction(InventoryCard card) {
        card.setAction(stockActionForQuantity(quantity(card)));
    }

    private String stockActionForQuantity(int quantity) {
        return quantity <= 0 ? ACTION_OUT_OF_STOCK : ACTION_IN_STOCK;
    }

    public record SearchResult(
            String name,
            String edition,
            String sku,
            String variation,
            String printing,
            String nmPrice,
            int stockQuantity,
            int rowIndex,
            String cardId,
            PriceComparison prices,
            double localPrice
    ) {
        /** Precio de venta de referencia para ofertar en una compra: el del Sheet, o el sugerido si no hay. */
        public long referencePrice() {
            return Math.round(localPrice > 0 ? localPrice : prices == null ? 0 : prices.suggestedValue());
        }

        public String formattedNmPrice() {
            return formatUsdPrice(nmPrice);
        }
    }

    public record CardSuggestion(
            String name,
            String variation
    ) {
    }

    public record UpdateResult(
            String name,
            String edition,
            String setCode,
            String collectorNumber,
            String localPrice,
            String ckPriceUsd,
            String action,
            int rowIndex,
            int stockQuantity,
            boolean writeRequired
    ) {
        public String formattedLocalPrice() {
            return formatLocalPrice(localPrice);
        }

        public String formattedCkPriceUsd() {
            return formatUsdPrice(ckPriceUsd);
        }
    }

    public record MovementMonthGroup(
            String key,
            String label,
            int count,
            boolean open,
            List<InventoryMovement> movements
    ) {
    }

    public record CashMonthGroup(
            String key,
            String label,
            int count,
            boolean open,
            List<CashDayGroup> days
    ) {
    }

    public record CashDayGroup(
            String date,
            List<CashRegisterEntry> entries,
            String total,
            String formattedTotal,
            boolean open
    ) {
        public String formattedDate() {
            try {
                return LocalDate.parse(date, MOVEMENT_DATE_FORMAT)
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } catch (RuntimeException e) {
                return date == null ? "" : date;
            }
        }
    }

    public record CashReportMonth(
            String key,
            String label,
            String totalSales,
            int soldQuantity,
            int enteredQuantity,
            int balanceQuantity,
            String salesWidth,
            String soldWidth,
            String enteredWidth
    ) {
    }

    private class CashReportAccumulator {
        private double salesTotal;
        private int soldQuantity;
        private int enteredQuantity;

        void addCard(CashReportCardAccumulator cardReport) {
            salesTotal += cardReport.salesTotal;
            soldQuantity += cardReport.soldQuantity;
            enteredQuantity += cardReport.enteredQuantity;
        }

        CashReportMonth toReport(
                String key,
                String label,
                double maxSales,
                int maxMovementQuantity
        ) {
            return new CashReportMonth(
                    key,
                    label,
                    formatCashTotal(salesTotal),
                    soldQuantity,
                    enteredQuantity,
                    enteredQuantity - soldQuantity,
                    percent(salesTotal, maxSales),
                    percent(soldQuantity, maxMovementQuantity),
                    percent(enteredQuantity, maxMovementQuantity)
            );
        }
    }

    private static class CashReportCardAccumulator {
        private double salesTotal;
        private int soldQuantity;
        private int enteredQuantity;

        void addSaleTotal(double total) {
            salesTotal += total;
        }

        void addSold(int quantity) {
            soldQuantity += quantity;
        }

        void addEntry(int quantity) {
            enteredQuantity += quantity;
        }

    }

    private class MovementAccumulator {
        private final InventoryMovement firstMovement;
        private String dateTime;
        private String time;
        private String type;
        private int quantity;
        private String newStock;
        private boolean initialized;

        MovementAccumulator(InventoryMovement firstMovement) {
            this.firstMovement = firstMovement;
        }

        void add(InventoryMovement movement) {
            if (!initialized) {
                dateTime = movement.getDateTime();
                time = movement.getTime();
                initialized = true;
            }

            quantity += signedQuantity(movement);
            dateTime = movement.getDateTime();
            time = movement.getTime();
            type = quantity < 0 ? "SALIDA" : "ENTRADA";
            newStock = movement.getNewStock();
        }

        InventoryMovement toMovement() {
            return new InventoryMovement(
                    dateTime,
                    firstMovement.getDate(),
                    time,
                    type,
                    formattedMovementQuantity(quantity),
                    firstMovement.getName(),
                    firstMovement.getSetName(),
                    firstMovement.getSetCode(),
                    firstMovement.getCollectorNumber(),
                    firstMovement.getPrinting(),
                    firstMovement.getPreviousStock(),
                    newStock,
                    stockAction(quantity)
            );
        }
    }

    public record ParsedImportLine(
            String originalLine,
            int quantity,
            String name,
            String setCode,
            String collectorNumber,
            Boolean foil,
            int duplicateCount
    ) {
    }

    public record ImportResult(
            String originalLine,
            int quantity,
            String name,
            String edition,
            String sku,
            String variation,
            String printing,
            String nmPrice,
            int stockQuantity,
            int rowIndex,
            String status,
            boolean selectable,
            List<ImportOption> alternatives
    ) {
        public String formattedNmPrice() {
            return formatUsdPrice(nmPrice);
        }
    }

    public record ImportOption(
            int quantity,
            String name,
            String edition,
            String sku,
            String variation,
            String printing,
            String nmPrice,
            int stockQuantity
    ) {
        public String formattedNmPrice() {
            return formatUsdPrice(nmPrice);
        }
    }

    @PostMapping("/tutorial/completar")
    public ResponseEntity<Void> completeTutorial() {

        try {

            storeSettingsService.completeTutorial();

            return ResponseEntity.ok().build();

        } catch (Exception e) {

            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/tutorial/reset")
    public String resetTutorial() {
        try {

            storeSettingsService.resetTutorial();

        } catch (Exception e) {
            log.warn("No se pudo reiniciar el tutorial.", e);
        }
        return "redirect:/";
    }

    @PostMapping("/inventory/quantity")
    @ResponseBody
    public ResponseEntity<?> updateQuantity(
            @RequestParam(defaultValue = "0") int rowIndex,
            @RequestParam(required = false) String cardId,
            @RequestParam int change,
            @RequestParam(required = false) String reason
    ) {
        try {
            var result = stockOperationsService.changeStock(
                    cardId,
                    rowIndex,
                    change,
                    StockOperationsService.Reason.from(reason)
            );

            InventoryCard card = findInventoryCardByRow(result.rowIndex());
            if (card != null) {
                refreshLatestUpdateForCard(card);
            }

            return ResponseEntity.ok(new StockUpdateResponse(
                    result.rowIndex(),
                    result.cardId(),
                    result.stockQuantity(),
                    result.action(),
                    result.source()
            ));
        } catch (StockOperationsService.StockOperationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiMessage(false, e.getMessage()));
        } catch (Exception e) {
            log.warn("No se pudo actualizar la cantidad de inventario.", e);
            return ResponseEntity.internalServerError()
                    .body(new ApiMessage(false, "No se pudo actualizar el stock: " + e.getMessage()));
        }
    }

    @PostMapping("/caja/anular")
    public String voidSale(@RequestParam String saleId, RedirectAttributes redirectAttributes) {
        try {
            var result = stockOperationsService.voidSale(saleId);
            redirectAttributes.addFlashAttribute("cashMessage", result.stockRestored()
                    ? "Venta anulada: se restó de la caja y las cartas volvieron al stock."
                    : "Venta anulada en caja. No se encontró la carta en el inventario, revisá el stock a mano.");
        } catch (StockOperationsService.StockOperationException e) {
            redirectAttributes.addFlashAttribute("cashError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo anular la venta {}.", saleId, e);
            redirectAttributes.addFlashAttribute("cashError", "No se pudo anular la venta: " + e.getMessage());
        }

        return "redirect:/movimientos?tab=cash";
    }

    /**
     * Compra de cartas a un cliente. Las líneas llegan como JSON [{sku, cardId, quantity, unitPaid}].
     * Las cartas que todavía no están en el Sheet se agregan (respetando el límite del plan) y después
     * BuyService suma el stock y registra el pago.
     */
    @PostMapping("/compras")
    public String buyCards(
            @RequestParam String lines,
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String contact,
            @RequestParam(defaultValue = "cash") String payment,
            RedirectAttributes redirect
    ) {
        try {
            com.google.gson.JsonArray array = com.google.gson.JsonParser.parseString(lines).getAsJsonArray();
            if (array.isEmpty()) {
                throw new StockOperationsService.StockOperationException("La compra no tiene cartas.");
            }
            com.tcg.bot.service.BuyService.Result result;
            synchronized (StockOperationsService.writeLock()) {
                List<InventoryCard> current = new ArrayList<>(inventoryService.getInventoryCards());
                List<com.tcg.bot.service.BuyService.Line> buyLines = new ArrayList<>();
                for (var element : array) {
                    var line = element.getAsJsonObject();
                    String cardId = line.has("cardId") && !line.get("cardId").isJsonNull() ? line.get("cardId").getAsString() : "";
                    String sku = line.has("sku") && !line.get("sku").isJsonNull() ? line.get("sku").getAsString() : "";
                    int quantity = line.has("quantity") ? line.get("quantity").getAsInt() : 0;
                    double unitPaid = line.has("unitPaid") ? line.get("unitPaid").getAsDouble() : -1;

                    InventoryCard card = current.stream()
                            .filter(candidate -> !cardId.isBlank() && cardId.equals(candidate.getId()))
                            .findFirst()
                            .orElse(null);
                    if (card == null) {
                        CardKingdomProduct product = findProductBySku(sku);
                        if (product == null) {
                            throw new StockOperationsService.StockOperationException(
                                    "No se encontró una de las cartas en Card Kingdom. Volvé a buscarla.");
                        }
                        card = findSamePrinting(current, product);
                        if (card == null) {
                            if (licenseService.remainingNewCards(distinctCards(current)) <= 0) {
                                throw new StockOperationsService.StockOperationException(licenseService.limitMessage());
                            }
                            card = createInventoryCard(product);
                            card.setQuantity("0");
                            card.setAction(StockOperationsService.actionFor(0));
                            card.setRowIndex(inventoryService.appendInventoryCard(card));
                            current.add(card);
                        }
                    }
                    buyLines.add(new com.tcg.bot.service.BuyService.Line(card, quantity, unitPaid));
                }
                result = buyService.buy(customer, contact,
                        "credit".equalsIgnoreCase(payment) ? com.tcg.bot.service.BuyService.Payment.CREDIT
                                : com.tcg.bot.service.BuyService.Payment.CASH,
                        buyLines);
            }
            redirect.addFlashAttribute("buyMessage", result.payment() == com.tcg.bot.service.BuyService.Payment.CASH
                    ? "Compra registrada: " + result.units() + (result.units() == 1 ? " carta" : " cartas")
                            + " por $ " + result.formattedTotal() + " en efectivo. Ya están en el stock."
                    : "Compra registrada: " + result.units() + (result.units() == 1 ? " carta" : " cartas")
                            + ". " + customer.trim() + " ahora tiene $ " + result.formattedBalance() + " de crédito.");
            redirect.addFlashAttribute("clearBuy", true);
        } catch (StockOperationsService.StockOperationException e) {
            redirect.addFlashAttribute("buyError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo registrar la compra.", e);
            redirect.addFlashAttribute("buyError", "No se pudo registrar la compra: " + e.getMessage());
        }
        return "redirect:/compras";
    }

    @PostMapping("/inventory/cards")
    @ResponseBody
    public ResponseEntity<?> addInventoryCard(@RequestParam String sku) {
        try {
            CardKingdomProduct product = findProductBySku(sku);

            if (product == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiMessage(false, "No se encontro la carta en Card Kingdom."));
            }

            InventoryCard card;
            int rowIndex;
            synchronized (StockOperationsService.writeLock()) {
                List<InventoryCard> current = inventoryService.getInventoryCards();

                // Doble clic o dos pestañas: si la impresión ya está en el Sheet, se suma ahí en vez de duplicarla.
                InventoryCard existing = findSamePrinting(current, product);
                if (existing != null) {
                    var result = stockOperationsService.changeStock(existing.getId(), existing.getRowIndex(), 1,
                            StockOperationsService.Reason.ADJUST);
                    return ResponseEntity.ok(new StockCreateResponse(result.rowIndex(), result.cardId()));
                }

                if (licenseService.remainingNewCards(distinctCards(current)) <= 0) {
                    return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                            .body(new ApiMessage(false, licenseService.limitMessage()));
                }

                card = createInventoryCard(product);
                rowIndex = inventoryService.appendInventoryCard(card);
                card.setRowIndex(rowIndex);
                inventoryService.appendMovement(createMovement("ENTRADA", 1, card, 0, 1, "Agregado al stock"));
            }
            refreshLatestUpdateForCard(card);

            return ResponseEntity.ok(new StockCreateResponse(rowIndex, card.getId()));
        } catch (Exception e) {
            log.warn("No se pudo agregar la carta al inventario.", e);
            return ResponseEntity.internalServerError()
                    .body(new ApiMessage(false, "No se pudo agregar la carta al inventario: " + e.getMessage()));
        }
    }

    private InventoryCard findSamePrinting(List<InventoryCard> cards, CardKingdomProduct product) {
        String setCode = setCode(product.getSku());
        String number = collectorNumberForSheet(product.getSku());
        String printing = "true".equalsIgnoreCase(product.getFoil()) ? "foil" : "nonfoil";
        return cards.stream()
                .filter(card -> card.getName() != null && card.getName().equalsIgnoreCase(product.getName()))
                .filter(card -> card.getSetCode() != null && card.getSetCode().equalsIgnoreCase(setCode))
                .filter(card -> ScryfallPriceService.key(card.getSetCode(), card.getCollectorNumber())
                        .equals(ScryfallPriceService.key(setCode, number)))
                .filter(card -> printing.equalsIgnoreCase(card.getPrinting() == null ? "" : card.getPrinting()))
                .findFirst()
                .orElse(null);
    }

    private CardKingdomProduct findProductBySku(String sku) {
        if (sku == null || sku.isBlank()) {
            return null;
        }

        var priceList = cardKingdomApiService.getPriceList();

        if (priceList == null || priceList.getData() == null) {
            return null;
        }

        return priceList.getData().stream()
                .filter(product -> product.getSku() != null
                        && product.getSku().equalsIgnoreCase(sku.trim()))
                .findFirst()
                .orElse(null);
    }

    private InventoryCard createInventoryCard(CardKingdomProduct product) {
        InventoryCard card = new InventoryCard();
        card.setQuantity("1");
        card.setName(product.getName());
        card.setSetCode(setCode(product.getSku()));
        card.setSetName(product.getEdition());
        card.setCollectorNumber(collectorNumberForSheet(product.getSku()));
        card.setCondition("NM");
        card.setPrinting("true".equalsIgnoreCase(product.getFoil()) ? "foil" : "nonfoil");
        card.setLanguage("EN");
        Double ckPrice = priceComparisonService.getBestConditionPrice(product, card);

        if (ckPrice == null) {
            card.setLocalPrice("");
            card.setCkPriceUsd("");
        } else {
            // Mismo precio que muestra "Tu precio" en la búsqueda: respeta las fuentes elegidas.
            ScryfallPriceService.Prices extra = null;
            if (pricingSettingsService.usesExtraSources()) {
                extra = scryfallPriceService.prices(List.of(
                                new ScryfallPriceService.CardRef(card.getSetCode(), card.getCollectorNumber())))
                        .get(ScryfallPriceService.key(card.getSetCode(), card.getCollectorNumber()));
            }
            var quote = priceComparisonService.quote(ckPrice, extra, card.isFoil());
            double suggested = quote.localPrice() == null
                    ? priceComparisonService.calculateLocalPrice(ckPrice)
                    : quote.localPrice();
            String localPrice = String.format("%.0f", suggested);
            card.setLocalPrice(localPrice);
            card.setCkPriceUsd(String.format(java.util.Locale.US, "%.2f", ckPrice));
        }

        card.setAction(ACTION_IN_STOCK);
        return card;
    }

    private String setCode(String sku) {
        if (sku == null || !sku.contains("-")) {
            return "";
        }

        return sku.substring(0, sku.indexOf("-"));
    }

    private String collectorNumberForSheet(String sku) {
        if (sku == null || !sku.contains("-")) {
            return "";
        }

        return sku.substring(sku.indexOf("-") + 1);
    }

    public record StockCreateResponse(int rowIndex, String cardId) {
    }

    public record ApiMessage(boolean success, String message) {
    }

    public record StockUpdateResponse(
            int rowIndex,
            String cardId,
            int stockQuantity,
            String action,
            String message
    ) {
    }
}


