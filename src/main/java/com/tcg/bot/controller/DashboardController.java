package com.tcg.bot.controller;

import com.tcg.bot.dto.CardKingdomProduct;
import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.CardReservation;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.InventoryMovement;
import com.tcg.bot.model.ReservationClient;
import com.tcg.bot.model.ReservationConditionStock;
import com.tcg.bot.service.CardKingdomApiService;
import com.tcg.bot.service.InventoryService;
import com.tcg.bot.service.PriceComparisonService;
import com.tcg.bot.service.PricingSettingsService;
import com.tcg.bot.service.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.Set;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
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
    private volatile SuggestionIndex suggestionIndex = new SuggestionIndex(0, 0, List.of(), new ConcurrentHashMap<>());
    private static final Pattern LEADING_QUANTITY_PATTERN =
            Pattern.compile("^\\s*(\\d+)\\s*x?\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_QUANTITY_PATTERN =
            Pattern.compile("^\\s*(.+?)\\s+x\\s*(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SET_CODE_PATTERN =
            Pattern.compile("[\\[(]([A-Za-z0-9]{2,8})[\\])]");
    private static final Pattern COLLECTOR_PATTERN =
            Pattern.compile("(?:#|\\s)([A-Za-z0-9]+(?:-[A-Za-z0-9]+)?)\\s*$");
    private static final Pattern VARIATION_STYLE_PREFIX_PATTERN =
            Pattern.compile("(?i)^(?:\\d+\\s*-\\s*)?(?:(?:surge\\s+foil|etched\\s+foil|foil\\s+etched|foil|nonfoil|non-foil|borderless|extended\\s+art|showcase|retro\\s+frame|alternate\\s+art|alt\\s+art|full\\s+art|textured\\s+foil|promo\\s+pack|prerelease\\s+foil|prerelease|release\\s+foil|fnm\\s+foil|judge\\s+foil|ripple\\s+foil|galaxy\\s+foil|halo\\s+foil|ampersand\\s+foil|bundle\\s+foil|resale\\s+foil|arena\\s+foil|store\\s+championship\\s+foil|buy-a-box\\s+foil|buy-a-box|b?a?b\\s+promo|prerelease\\s+promo|not\\s+tournament\\s+legal|pw\\s+symbol|no\\s+pw\\s+symbol|plane\\s+oversized|scheme\\s+oversized|oversized\\s+foil|oversized|planeswalker\\s+deck|commander\\s+deck|starter\\s+kit|theme\\s+booster|schematic\\s+art|textless|display\\s+commander|intro\\s+pack\\s+rare\\s+foil|eternal\\s+night|gilded\\s+foil|dossier|magnified|commandfest\\s+foil|commandfest\\s+non-foil|magicfest\\s+foil|magicfest\\s+non-foil|festival\\s+foil|festival\\s+non-foil)\\s*-\\s*)+");
    private static final Pattern VARIATION_STYLE_SUFFIX_PATTERN =
            Pattern.compile("(?i)\\s*-\\s*(?:surge\\s+foil|etched\\s+foil|foil\\s+etched|foil|nonfoil|non-foil|traditional\\s+foil|borderless|extended\\s+art|showcase|retro\\s+frame|textured\\s+foil|ripple\\s+foil|galaxy\\s+foil|halo\\s+foil|confetti\\s+foil|double\\s+rainbow\\s+foil|pool\\s+party\\s+foil|commandfest\\s+foil|commandfest\\s+non-foil|magicfest\\s+foil|magicfest\\s+non-foil|festival\\s+foil|festival\\s+non-foil|festival\\s+foil\\s+etched|promo\\s+foil|promo\\s+non-foil|commander\\s+deck|not\\s+tournament\\s+legal)$");
    private static final Pattern VARIATION_STYLE_ONLY_PATTERN =
            Pattern.compile("(?i)^(?:[a-z]|\\d+|\\d+\\s*-\\s*)?(?:surge\\s+foil|etched\\s+foil|foil\\s+etched|foil|nonfoil|non-foil|traditional\\s+foil|borderless|extended\\s+art|showcase|retro\\s+frame|alternate\\s+art|alt\\s+art|full\\s+art|textured\\s+foil|promo\\s+pack|prerelease\\s+foil|prerelease|release\\s+foil|fnm\\s+foil|judge\\s+foil|ripple\\s+foil|galaxy\\s+foil|halo\\s+foil|ampersand\\s+foil|bundle\\s+foil|resale\\s+foil|arena\\s+foil|store\\s+championship\\s+foil|buy-a-box\\s+foil|buy-a-box|b?a?b\\s+promo|prerelease\\s+promo|not\\s+tournament\\s+legal|pw\\s+symbol|no\\s+pw\\s+symbol|plane\\s+oversized|scheme\\s+oversized|oversized\\s+foil|oversized|planeswalker\\s+deck|commander\\s+deck|starter\\s+kit|theme\\s+booster|schematic\\s+art|textless|display\\s+commander|intro\\s+pack\\s+rare\\s+foil|eternal\\s+night|gilded\\s+foil|dossier|magnified|commandfest\\s+foil|commandfest\\s+non-foil|magicfest|magicfest\\s+foil|magicfest\\s+non-foil|festival\\s+foil|festival\\s+non-foil|normal)(?:\\s*-\\s*(?:surge\\s+foil|etched\\s+foil|foil\\s+etched|foil|nonfoil|non-foil|borderless|extended\\s+art|showcase|retro\\s+frame|alternate\\s+art|alt\\s+art|full\\s+art|textured\\s+foil|promo\\s+pack|prerelease\\s+foil|prerelease|release\\s+foil|fnm\\s+foil|judge\\s+foil|ripple\\s+foil|galaxy\\s+foil|halo\\s+foil|ampersand\\s+foil|bundle\\s+foil|resale\\s+foil|arena\\s+foil|store\\s+championship\\s+foil|buy-a-box\\s+foil|buy-a-box|b?a?b\\s+promo|prerelease\\s+promo|not\\s+tournament\\s+legal|pw\\s+symbol|no\\s+pw\\s+symbol|plane\\s+oversized|scheme\\s+oversized|oversized\\s+foil|oversized|planeswalker\\s+deck|commander\\s+deck|starter\\s+kit|theme\\s+booster|schematic\\s+art|textless|display\\s+commander|intro\\s+pack\\s+rare\\s+foil|eternal\\s+night|gilded\\s+foil|dossier|magnified|commandfest\\s+foil|commandfest\\s+non-foil|magicfest|magicfest\\s+foil|magicfest\\s+non-foil|festival\\s+foil|festival\\s+non-foil|normal))*$");
    private static final String ACTION_IN_STOCK = "En Stock";
    private static final String ACTION_RESERVED = "Reservada";
    private static final String ACTION_OUT_OF_STOCK = "Sin Stock";
    private static final Locale ARGENTINA_LOCALE = new Locale("es", "AR");
    private static final ZoneId APP_ZONE = ZoneId.of("America/Buenos_Aires");
    private static final DateTimeFormatter MOVEMENT_DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MOVEMENT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MOVEMENT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter PRICE_LIST_UPDATED_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String MOVEMENTS_ACCESS_SESSION_KEY = "movementsAccessUnlocked";
    private static final String MOVEMENTS_ACCESS_PASSWORD = "counterspell";

    public DashboardController(
            InventoryService inventoryService,
            CardKingdomApiService cardKingdomApiService,
            PriceComparisonService priceComparisonService,
            PricingSettingsService pricingSettingsService,
            StoreSettingsService storeSettingsService
    ) {
        this.inventoryService = inventoryService;
        this.cardKingdomApiService = cardKingdomApiService;
        this.priceComparisonService = priceComparisonService;
        this.pricingSettingsService = pricingSettingsService;
        this.storeSettingsService = storeSettingsService;
    }

    @PostConstruct
    public void warmSuggestionIndexCache() {
        Thread warmupThread = new Thread(() -> {
            try {
                var priceList = cardKingdomApiService.getPriceList();
                if (priceList != null && priceList.getData() != null) {
                    suggestionIndexFor(priceList.getData()).suggestionsFor("cent");
                    log.info("Indice de sugerencias CK precalentado.");
                }
            } catch (Exception e) {
                log.warn("No se pudo precalentar el indice de sugerencias.", e);
            }
        }, "card-suggestion-index-warmup");
        warmupThread.setDaemon(true);
        warmupThread.start();
    }

    @ModelAttribute("movementsUnlocked")
    public boolean movementsUnlocked(HttpServletRequest request) {
        return isMovementsUnlocked(request.getSession(false));
    }

    @Scheduled(fixedDelayString = "PT1H2M", initialDelayString = "PT1H2M")
    public void synchronizeInventoryAutomatically() {
        if (!storeSettingsService.hasSpreadsheetConfigured() || !inventoryService.hasCredentialsConfigured()) {
            log.info("Sincronizacion automatica omitida: faltan configuracion o credenciales.");
            return;
        }

        try {
            if (synchronizeInventory(true)) {
                log.info("Sincronizacion automatica completada.");
            } else {
                log.warn("Sincronizacion automatica omitida: no se pudo obtener la pricelist de Card Kingdom.");
            }
        } catch (Exception e) {
            log.warn("No se pudo ejecutar la sincronizacion automatica.", e);
        }
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
            SearchFields searchFields = dashboardSearchFields(trimmedQuery, trimmedSet, trimmedNumber);
            List<CardKingdomProduct> products;

            if (!searchFields.setFilter().isBlank() || !searchFields.numberFilter().isBlank()) {
                var priceList = cardKingdomApiService.getPriceList();
                products = priceList == null || priceList.getData() == null
                        ? List.of()
                        : searchDashboardProducts(priceList.getData(), searchFields);
            } else {
                products = cardKingdomApiService.searchProducts(buildSearchQuery(trimmedQuery, trimmedSet, trimmedNumber));
                products = filterProductsForNameQuery(products, searchFields.nameQuery());
            }

            if (!trimmedQuery.isBlank() && trimmedSet.isBlank() && trimmedNumber.isBlank() && products.isEmpty()) {
                model.addAttribute(
                        "searchFormatError",
                        "No se encontro esa carta o falta una coma. Para filtrar usa: Sol Ring, cmm."
                );
                return "dashboard";
            }

            var inventoryCards = inventoryService.getInventoryCards();
            var pendingReservationQuantities = pendingReservationQuantities();
            var results = products.stream()
                    .map(product -> createSearchResult(product, inventoryCards))
                    .toList();

            model.addAttribute("pendingReservationQuantities", pendingReservationQuantities);
            model.addAttribute("results", results);
            model.addAttribute("totalStockQuantity", results.stream()
                    .mapToInt(SearchResult::stockQuantity)
                    .sum());
        } catch (Exception e) {
            model.addAttribute("error", "No se pudo consultar Card Kingdom o el inventario configurado.");
        }

        return "dashboard";
    }

    private SearchFields dashboardSearchFields(String query, String setFilter, String numberFilter) {
        if (!query.isBlank() && setFilter.isBlank() && numberFilter.isBlank()) {
            ParsedImportLine parsedLine = parseImportLine(query);
            if (parsedLine != null
                    && (!parsedLine.setCode().isBlank() || !parsedLine.collectorNumber().isBlank())) {
                return new SearchFields(
                        parsedLine.name(),
                        parsedLine.setCode(),
                        parsedLine.collectorNumber()
                );
            }
        }

        return new SearchFields(query, setFilter, numberFilter);
    }

    private List<CardKingdomProduct> searchDashboardProducts(
            List<CardKingdomProduct> products,
            SearchFields searchFields
    ) {
        String normalizedName = normalizeSuggestionText(searchFields.nameQuery());

        return products.stream()
                .filter(product -> normalizedName.isBlank()
                        || matchesSuggestion(
                        product.getName(),
                        searchableVariationText(product.getVariation()),
                        normalizedName
                ))
                .filter(product -> searchFields.setFilter().isBlank()
                        || matchesDashboardSetField(product, searchFields.setFilter()))
                .filter(product -> searchFields.numberFilter().isBlank()
                        || matchesDashboardNumberField(product, searchFields.numberFilter()))
                .toList();
    }

    private boolean matchesDashboardSetField(CardKingdomProduct product, String setFilter) {
        return matchesLooseText(setCode(product.getSku()), setFilter)
                || matchesLooseText(product.getEdition(), setFilter)
                || matchesLooseText(product.getVariation(), setFilter);
    }

    private boolean matchesDashboardNumberField(CardKingdomProduct product, String numberFilter) {
        String normalizedNumber = normalizeImportCollector(numberFilter);
        if (normalizedNumber.isBlank()) {
            return true;
        }

        return importCollectorFromSku(product.getSku()).equals(normalizedNumber)
                || normalizeImportCollector(product.getVariation()).contains(normalizedNumber);
    }

    private boolean matchesLooseText(String value, String query) {
        String normalizedValue = normalizeSuggestionText(value);
        String normalizedQuery = normalizeSuggestionText(query);

        if (normalizedQuery.isBlank()) {
            return true;
        }

        String compactValue = normalizedValue.replace(" ", "");
        String compactQuery = normalizedQuery.replace(" ", "");
        compactValue = compactValue.replace("commanderfest", "commandfest");
        compactQuery = compactQuery.replace("commanderfest", "commandfest");
        return normalizedValue.contains(normalizedQuery)
                || (!compactQuery.isBlank() && compactValue.contains(compactQuery));
    }

    private List<CardKingdomProduct> filterProductsForNameQuery(
            List<CardKingdomProduct> products,
            String query
    ) {
        String nameQuery = searchableNameQuery(query);
        if (nameQuery.isBlank()) {
            return products;
        }

        String normalizedQuery = normalizeSuggestionText(nameQuery);
        return products.stream()
                .filter(product -> matchesSuggestion(
                        product.getName(),
                        searchableVariationText(product.getVariation()),
                        normalizedQuery
                ))
                .toList();
    }

    private String searchableNameQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }

        ParsedImportLine parsedLine = parseImportLine(query);
        if (parsedLine != null) {
            return parsedLine.name();
        }

        return query;
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
        if (!query.isBlank() && setFilter.isBlank() && numberFilter.isBlank()) {
            ParsedImportLine parsedLine = parseImportLine(query);
            if (parsedLine != null
                    && (!parsedLine.setCode().isBlank() || !parsedLine.collectorNumber().isBlank())) {
                return buildSearchQuery(
                        parsedLine.name(),
                        parsedLine.setCode(),
                        parsedLine.collectorNumber()
                );
            }
        }

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

            return ResponseEntity.ok(suggestionIndexFor(priceList.getData()).suggestionsFor(normalizedQuery));
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    private SuggestionIndex suggestionIndexFor(List<CardKingdomProduct> products) {
        int identity = System.identityHashCode(products);
        SuggestionIndex current = suggestionIndex;

        if (current.identity() == identity && current.size() == products.size()) {
            return current;
        }

        synchronized (this) {
            current = suggestionIndex;
            if (current.identity() == identity && current.size() == products.size()) {
                return current;
            }

            List<SuggestionCandidate> candidates = new ArrayList<>();

            for (CardKingdomProduct product : products) {
                String name = product.getName();
                if (name == null || name.isBlank()) {
                    continue;
                }

                String variation = product.getVariation();
                String searchableVariation = searchableVariationText(variation);
                candidates.add(new SuggestionCandidate(
                        name,
                        searchableVariation,
                        normalizeSuggestionText(name),
                        normalizeSuggestionText(searchableVariation),
                        1
                ));

                String variationAlias = suggestionVariationAlias(variation);
                if (!variationAlias.isBlank()
                        && !normalizeSuggestionText(variationAlias).equals(normalizeSuggestionText(name))) {
                    candidates.add(new SuggestionCandidate(
                            variationAlias,
                            name,
                            normalizeSuggestionText(variationAlias),
                            normalizeSuggestionText(name + " " + variationAlias),
                            0
                    ));
                }
            }

            SuggestionIndex rebuilt = new SuggestionIndex(
                    identity,
                    products.size(),
                    List.copyOf(candidates),
                    new ConcurrentHashMap<>()
            );
            suggestionIndex = rebuilt;
            return rebuilt;
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

    private String suggestionVariationAlias(String variation) {
        return searchableVariationText(variation);
    }

    private String searchableVariationText(String variation) {
        if (variation == null || variation.isBlank()) {
            return "";
        }

        String candidate = variation.trim()
                .replaceFirst("^\\d+\\s*-\\s*", "")
                .trim();

        if (isEditionStyleText(candidate)) {
            return "";
        }

        candidate = VARIATION_STYLE_PREFIX_PATTERN.matcher(candidate)
                .replaceFirst("")
                .trim();

        String previousCandidate;
        do {
            previousCandidate = candidate;
            candidate = VARIATION_STYLE_SUFFIX_PATTERN.matcher(candidate)
                    .replaceFirst("")
                    .trim();
        } while (!candidate.equals(previousCandidate));

        return isEditionStyleText(candidate) ? "" : candidate;
    }

    private boolean isEditionStyleText(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }

        String trimmed = value.trim();
        return trimmed.matches("(?i)^[a-z]$")
                || trimmed.matches("^\\d+$")
                || VARIATION_STYLE_ONLY_PATTERN.matcher(trimmed).matches();
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
            HttpServletRequest request,
            Model model
    ) {
        addBaseModel(model, "");
        boolean movementsLocked = !isMovementsUnlocked(request.getSession(false));

        String selectedMovementDate = movementDate == null ? "" : movementDate.trim();
        String selectedCashDate = cashDate == null ? "" : cashDate.trim();
        String activeTab = activeMovementTab(tab, selectedMovementDate, selectedCashDate);

        model.addAttribute("selectedMovementDate", selectedMovementDate);
        model.addAttribute("selectedCashDate", selectedCashDate);
        model.addAttribute("activeTab", activeTab);

        if (movementsLocked) {
            addLockedMovementPreviewModel(model, request);
            return "movements";
        }

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
            addMovementLockModel(model, movementsLocked, request);
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
        addMovementLockModel(model, movementsLocked, request);
        return "movements";
    }

    private void addMovementLockModel(Model model, boolean movementsLocked, HttpServletRequest request) {
        model.addAttribute("movementsLocked", movementsLocked);
        if (movementsLocked) {
            model.addAttribute("returnTo", movementAccessReturnPath(request));
        }
    }

    private void addLockedMovementPreviewModel(Model model, HttpServletRequest request) {
        model.addAttribute("movements", List.of());
        model.addAttribute("movementGroups", List.of());
        model.addAttribute("movementCount", 0);
        model.addAttribute("movementCountLabel", "movimientos hoy");
        model.addAttribute("cashEntries", List.of());
        model.addAttribute("cashGroups", List.of());
        model.addAttribute("cashTodayTotal", "0");
        model.addAttribute("cashSelectedTotal", "0");
        model.addAttribute("cashReportMonths", List.of());
        addMovementLockModel(model, true, request);
    }

    @PostMapping("/movimientos/acceso")
    public String unlockMovements(
            @RequestParam(name = "password", required = false) String password,
            @RequestParam(name = "returnTo", required = false) String returnTo,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        if (MOVEMENTS_ACCESS_PASSWORD.equals(password == null ? "" : password.trim())) {
            request.getSession(true).setAttribute(MOVEMENTS_ACCESS_SESSION_KEY, true);
            return "redirect:" + safeMovementAccessReturnPath(returnTo);
        }

        redirectAttributes.addFlashAttribute("accessError", "Contraseña incorrecta.");
        return "redirect:" + safeMovementAccessReturnPath(returnTo);
    }

    @GetMapping("/reservas")
    public String reservations(Model model) {
        addBaseModel(model, "");
        model.addAttribute("reservationStatuses", reservationStatuses());

        try {
            List<CardReservation> reservations = inventoryService.getReservations();
            reservations = consolidateDuplicateReservations(reservations);
            List<InventoryCard> inventoryCards = inventoryService.getInventoryCards();
            var priceList = cardKingdomApiService.getPriceList();
            decorateReservationsWithStock(
                    reservations,
                    inventoryCards,
                    priceList == null || priceList.getData() == null ? List.of() : priceList.getData()
            );
            model.addAttribute("reservations", reservations);
            model.addAttribute("reservationGroups", reservationGroups(reservations));
            model.addAttribute("reservationCount", reservations.size());
            model.addAttribute("reservedCount", reservations.stream()
                    .filter(reservation -> CardReservation.STATUS_RESERVED.equalsIgnoreCase(reservation.getStatus()))
                    .count());
            model.addAttribute("wantedCount", reservations.stream()
                    .filter(reservation -> CardReservation.STATUS_WANTED.equalsIgnoreCase(reservation.getStatus()))
                    .count());
        } catch (Exception e) {
            log.warn("No se pudieron cargar las reservas.", e);
            model.addAttribute("reservations", List.of());
            model.addAttribute("reservationGroups", List.of());
            model.addAttribute("reservationCount", 0);
            model.addAttribute("reservedCount", 0);
            model.addAttribute("wantedCount", 0);
            model.addAttribute("error", "No se pudieron cargar las reservas: " + syncErrorMessage(e));
        }

        return "reservations";
    }

    private List<ReservationGroupView> reservationGroups(List<CardReservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return List.of();
        }

        Map<String, List<CardReservation>> reservationsByCustomer = new LinkedHashMap<>();
        for (CardReservation reservation : reservations) {
            reservationsByCustomer
                    .computeIfAbsent(customerReservationKey(reservation), key -> new ArrayList<>())
                    .add(reservation);
        }

        List<ReservationGroupView> groups = new ArrayList<>();
        for (Map.Entry<String, List<CardReservation>> entry : reservationsByCustomer.entrySet()) {
            List<CardReservation> customerReservations = entry.getValue();
            CardReservation first = customerReservations.get(0);
            int totalQuantity = customerReservations.stream()
                    .mapToInt(reservation -> reservationQuantity(reservation.getQuantity()))
                    .sum();
            int availableQuantity = customerReservations.stream()
                    .mapToInt(reservation -> Math.min(
                            reservation.getAvailableStock(),
                            reservationQuantity(reservation.getQuantity())
                    ))
                    .sum();
            double totalPrice = customerReservations.stream()
                    .mapToDouble(CardReservation::getLineTotalPrice)
                    .sum();
            double deliverableTotalPrice = customerReservations.stream()
                    .mapToDouble(CardReservation::getDeliverableTotalPrice)
                    .sum();

            groups.add(new ReservationGroupView(
                    entry.getKey(),
                    blankToDash(first.getClient()),
                    blankToDash(first.getPhone()),
                    blankToDash(first.getDni()),
                    reservationGroupStatus(customerReservations),
                    customerReservations.size(),
                    totalQuantity,
                    availableQuantity,
                    reservationGroupStatusLabel(customerReservations, availableQuantity, totalQuantity),
                    formatCashTotal(totalPrice),
                    formatCashTotal(deliverableTotalPrice),
                    first.getFormattedReservationDate(),
                    first.getFormattedPickupDate(),
                    first.getFormattedPaymentDate(),
                    customerReservations
            ));
        }

        return groups;
    }

    private List<CardReservation> consolidateDuplicateReservations(List<CardReservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return List.of();
        }

        Map<String, CardReservation> consolidatedByKey = new LinkedHashMap<>();
        for (CardReservation reservation : reservations) {
            String key = duplicateReservationKey(reservation);
            CardReservation existing = consolidatedByKey.get(key);
            if (existing == null) {
                reservation.setSourceRowIndexes(new ArrayList<>(List.of(reservation.getRowIndex())));
                consolidatedByKey.put(key, reservation);
                continue;
            }

            existing.setQuantity(String.valueOf(
                    reservationQuantity(existing.getQuantity()) + reservationQuantity(reservation.getQuantity())
            ));
            existing.getSourceRowIndexes().add(reservation.getRowIndex());
        }

        return new ArrayList<>(consolidatedByKey.values());
    }

    private String duplicateReservationKey(CardReservation reservation) {
        return String.join("|",
                customerReservationKey(reservation),
                normalizedCardText(reservation.getStatus()),
                normalizedCardText(reservation.getName()),
                normalizedCardText(reservation.getSetName()),
                normalizedCardText(reservation.getSetCode()),
                collectorNumber(reservation.getCollectorNumber()),
                normalizedPrintingForReservation(reservation.getPrinting()),
                normalizedCardText(reservation.getPickupDate()),
                normalizedCardText(reservation.getNotes())
        );
    }

    private String customerReservationKey(CardReservation reservation) {
        String phone = normalizedCardText(reservation.getPhone());
        String dni = normalizedCardText(reservation.getDni());
        String client = normalizedCardText(reservation.getClient());

        if (!phone.isBlank()) {
            return "phone:" + phone;
        }

        if (!dni.isBlank()) {
            return "dni:" + dni;
        }

        return "client:" + client;
    }

    private String reservationGroupStatus(List<CardReservation> reservations) {
        if (reservations.stream().anyMatch(reservation -> CardReservation.STATUS_RESERVED.equalsIgnoreCase(reservation.getStatus()))) {
            return CardReservation.STATUS_RESERVED;
        }

        if (reservations.stream().anyMatch(reservation -> CardReservation.STATUS_WANTED.equalsIgnoreCase(reservation.getStatus()))) {
            return CardReservation.STATUS_WANTED;
        }

        return CardReservation.STATUS_IN_STOCK;
    }

    private String reservationGroupStatusLabel(
            List<CardReservation> reservations,
            int availableQuantity,
            int totalQuantity
    ) {
        if (reservations.stream().anyMatch(reservation -> CardReservation.STATUS_RESERVED.equalsIgnoreCase(reservation.getStatus()))) {
            return totalQuantity + " " + cardQuantityWord(totalQuantity) + " reservadas"
                    + (availableQuantity > 0 ? " | " + availableQuantity + " disponibles" : "");
        }

        if (reservations.stream().anyMatch(reservation -> CardReservation.STATUS_WANTED.equalsIgnoreCase(reservation.getStatus()))) {
            return availableQuantity + " " + cardQuantityWord(availableQuantity) + " disponibles de "
                    + totalQuantity + " " + reservedQuantityWord(totalQuantity);
        }

        return reservationGroupStatus(reservations);
    }

    private String cardQuantityWord(int quantity) {
        return quantity == 1 ? "carta" : "cartas";
    }

    private String reservedQuantityWord(int quantity) {
        return quantity == 1 ? "reservada" : "reservadas";
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String reservationLookupKey(
            String name,
            String setName,
            String setCode,
            String collectorNumber,
            String printing
    ) {
        return lookupText(name)
                + "|" + lookupText(setName)
                + "|" + lookupText(setCode)
                + "|" + lookupCollectorNumber(collectorNumber)
                + "|" + lookupPrinting(printing);
    }

    private static String lookupText(String value) {
        return value == null
                ? ""
                : java.text.Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private static String lookupCollectorNumber(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim();
        int slashIndex = trimmed.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex + 1 < trimmed.length()) {
            trimmed = trimmed.substring(slashIndex + 1);
        }

        return trimmed.replaceAll("^0+(?=\\d)", "").toLowerCase(Locale.ROOT);
    }

    private static String lookupPrinting(String value) {
        String normalized = lookupText(value);
        if (normalized.equals("foil")) {
            return "foil";
        }

        return "nonfoil";
    }

    public class PendingReservationInfo {
        private int quantity;
        private final Set<String> clients = new LinkedHashSet<>();

        public void add(String client, int quantity) {
            this.quantity += Math.max(quantity, 1);
            if (!isBlank(client)) {
                clients.add(client.trim());
            }
        }

        public int getQuantity() {
            return quantity;
        }

        public String getClientsLabel() {
            return clients.isEmpty() ? "cliente sin nombre" : String.join(", ", clients);
        }

        public String getTooltip() {
            return "Carta pedida para " + getClientsLabel() + ".";
        }
    }

    private void decorateReservationsWithStock(
            List<CardReservation> reservations,
            List<InventoryCard> inventoryCards,
            List<CardKingdomProduct> products
    ) {
        Map<Integer, Integer> remainingStockByInventoryRow = new HashMap<>();

        for (CardReservation reservation : reservations) {
            InventoryCard card = findInventoryCardForReservation(reservation, inventoryCards);
            if (card == null) {
                reservation.setInventoryRowIndex(0);
                reservation.setAvailableStock(0);
                reservation.setCurrentStock(0);
                reservation.setDeliverableStock(0);
                reservation.setDisplayStatus(reservation.getStatus());
                reservation.setConditionStocks(conditionStocksForReservation(reservation, inventoryCards));
                applyReservationPriceFromProduct(reservation, products);
                continue;
            }

            reservation.setInventoryRowIndex(card.getRowIndex());
            reservation.setLocalPrice(card.getLocalPrice());
            reservation.setFormattedLocalPrice(formatLocalPrice(card.getLocalPrice()));
            reservation.setLineTotalPrice(lineTotalPrice(card.getLocalPrice(), reservationQuantity(reservation.getQuantity())));
            reservation.setFormattedLineTotalPrice(formatCashTotal(reservation.getLineTotalPrice()));
            reservation.setConditionStocks(conditionStocksForReservation(reservation, inventoryCards));
            int stockQuantity = quantity(card);
            if (isReservedStatus(card.getAction())
                    && !CardReservation.STATUS_RESERVED.equalsIgnoreCase(reservation.getStatus())) {
                stockQuantity = 0;
            }
            int initialStockQuantity = stockQuantity;
            int remainingStock = remainingStockByInventoryRow.computeIfAbsent(card.getRowIndex(), unused -> initialStockQuantity);
            reservation.setCurrentStock(stockQuantity);
            reservation.setAvailableStock(remainingStock);
            reservation.setDeliverableStock(remainingStock);
            reservation.setDisplayStatus(
                    CardReservation.STATUS_WANTED.equalsIgnoreCase(reservation.getStatus()) && stockQuantity > 0
                            ? CardReservation.STATUS_IN_STOCK
                            : reservation.getStatus()
            );
            int deliverableQuantity = Math.min(remainingStock, reservationQuantity(reservation.getQuantity()));
            reservation.setDeliverableTotalPrice(lineTotalPrice(card.getLocalPrice(), deliverableQuantity));
            reservation.setFormattedDeliverableTotalPrice(formatCashTotal(reservation.getDeliverableTotalPrice()));
            remainingStockByInventoryRow.put(
                    card.getRowIndex(),
                    Math.max(remainingStock - reservationQuantity(reservation.getQuantity()), 0)
            );
        }
    }

    private List<ReservationConditionStock> conditionStocksForReservation(
            CardReservation reservation,
            List<InventoryCard> inventoryCards
    ) {
        if (reservation == null || inventoryCards == null || inventoryCards.isEmpty()) {
            return List.of();
        }

        List<ReservationConditionStock> stocks = new ArrayList<>();
        for (String condition : List.of("NM", "EX", "VG", "G")) {
            List<InventoryCard> matches = inventoryCards.stream()
                    .filter(card -> matchesReservationInventoryCard(reservation, card))
                    .filter(card -> displayCondition(card.getCondition()).equals(condition))
                    .toList();

            int quantity = matches.stream().mapToInt(this::quantity).sum();
            int reservedQuantity = matches.stream()
                    .filter(card -> isReservedStatus(card.getAction()))
                    .mapToInt(this::quantity)
                    .sum();

            if (quantity <= 0 && reservedQuantity <= 0) {
                continue;
            }

            stocks.add(new ReservationConditionStock(
                    condition,
                    quantity,
                    Math.max(quantity - reservedQuantity, 0),
                    reservedQuantity,
                    reservedQuantity > 0 && reservedQuantity >= quantity ? ACTION_RESERVED : ACTION_IN_STOCK
            ));
        }

        long stockedConditionCount = stocks.stream()
                .filter(stock -> stock.quantity() > 0)
                .map(ReservationConditionStock::condition)
                .distinct()
                .count();

        return stockedConditionCount > 1 ? stocks : List.of();
    }

    private void applyReservationPriceFromProduct(
            CardReservation reservation,
            List<CardKingdomProduct> products
    ) {
        CardKingdomProduct product = products.stream()
                .filter(candidate -> matchesReservationProduct(reservation, candidate))
                .findFirst()
                .orElse(null);

        if (product == null) {
            return;
        }

        InventoryCard priceCard = inventoryCardFromReservation(reservation, 1);
        Double ckPrice = priceComparisonService.getBestConditionPrice(product, priceCard);
        if (ckPrice == null) {
            return;
        }

        String localPrice = String.format("%.0f", priceComparisonService.calculateLocalPrice(ckPrice));
        reservation.setLocalPrice(localPrice);
        reservation.setFormattedLocalPrice(formatLocalPrice(localPrice));
        reservation.setLineTotalPrice(lineTotalPrice(localPrice, reservationQuantity(reservation.getQuantity())));
        reservation.setFormattedLineTotalPrice(formatCashTotal(reservation.getLineTotalPrice()));
    }

    private boolean matchesReservationProduct(CardReservation reservation, CardKingdomProduct product) {
        if (product == null || product.getSku() == null) {
            return false;
        }

        return normalizedCardText(reservation.getName()).equals(normalizedCardText(product.getName()))
                && (isBlank(reservation.getSetName())
                || normalizedCardText(reservation.getSetName()).equals(normalizedCardText(product.getEdition())))
                && (isBlank(reservation.getSetCode())
                || reservation.getSetCode().trim().equalsIgnoreCase(setCode(product.getSku())))
                && (isBlank(reservation.getCollectorNumber())
                || collectorNumber(reservation.getCollectorNumber()).equals(collectorNumber(product.getSku())))
                && normalizedPrintingForReservation(reservation.getPrinting())
                .equals(normalizedPrintingForReservation("true".equalsIgnoreCase(product.getFoil()) ? "foil" : "nonfoil"));
    }

    private double lineTotalPrice(String localPrice, int quantity) {
        Double parsedPrice = parsePriceValue(localPrice);
        return parsedPrice == null ? 0 : parsedPrice * quantity;
    }

    private int returnReservedCardToStock(CardReservation reservation, HttpServletRequest request) throws Exception {
        return returnReservedCardToStock(reservation, request, inventoryService.getInventoryCards());
    }

    private int returnReservedCardToStock(
            CardReservation reservation,
            HttpServletRequest request,
            List<InventoryCard> inventoryCards
    ) throws Exception {
        int quantityToReturn = reservationQuantity(reservation.getQuantity());
        if (quantityToReturn <= 0) {
            return 0;
        }

        InventoryCard card = findInventoryCardForReservation(reservation, inventoryCards);
        int previousQuantity = card == null ? 0 : quantity(card);

        if (card == null) {
            card = inventoryCardFromReservation(reservation, quantityToReturn);
            int rowIndex = inventoryService.appendInventoryCard(card);
            card.setRowIndex(rowIndex);
            inventoryCards.add(card);
        } else {
            card.setQuantity(String.valueOf(previousQuantity + quantityToReturn));
            card.setAction(ACTION_IN_STOCK);
            inventoryService.updateStockState(card.getRowIndex(), card);
        }

        if (movementsModuleEnabled(request)) {
            inventoryService.appendMovement(createMovement(
                    "ENTRADA",
                    quantityToReturn,
                    card,
                    previousQuantity,
                    previousQuantity + quantityToReturn,
                    "Reserva cancelada"
            ));
        }

        refreshLatestUpdateForCard(card);
        return quantityToReturn;
    }

    private InventoryCard findInventoryCardForReservation(CardReservation reservation) throws Exception {
        return findInventoryCardForReservation(reservation, inventoryService.getInventoryCards());
    }

    private InventoryCard findInventoryCardForReservation(
            CardReservation reservation,
            List<InventoryCard> inventoryCards
    ) {
        return inventoryCards.stream()
                .filter(card -> matchesReservationInventoryCard(reservation, card))
                .findFirst()
                .orElse(null);
    }

    private boolean matchesReservationInventoryCard(CardReservation reservation, InventoryCard card) {
        if (!normalizedCardText(reservation.getName()).equals(normalizedCardText(card.getName()))) {
            return false;
        }

        if (!isBlank(reservation.getSetName())
                && !isBlank(card.getSetName())
                && !normalizedCardText(reservation.getSetName()).equals(normalizedCardText(card.getSetName()))) {
            return false;
        }

        if (!isBlank(reservation.getSetCode())
                && !isBlank(card.getSetCode())
                && !reservation.getSetCode().trim().equalsIgnoreCase(card.getSetCode().trim())) {
            return false;
        }

        if (!isBlank(reservation.getCollectorNumber())
                && !isBlank(card.getCollectorNumber())
                && !collectorNumber(reservation.getCollectorNumber()).equals(collectorNumber(card.getCollectorNumber()))) {
            return false;
        }

        return normalizedPrintingForReservation(reservation.getPrinting())
                .equals(normalizedPrintingForReservation(card.getPrinting()));
    }

    private InventoryCard inventoryCardFromReservation(CardReservation reservation, int quantity) {
        InventoryCard card = new InventoryCard();
        card.setQuantity(String.valueOf(quantity));
        card.setName(blankToEmpty(reservation.getName()));
        card.setSetCode(blankToEmpty(reservation.getSetCode()));
        card.setSetName(blankToEmpty(reservation.getSetName()));
        card.setCollectorNumber(blankToEmpty(reservation.getCollectorNumber()));
        card.setCondition("NM");
        card.setPrinting(blankToEmpty(reservation.getPrinting()));
        card.setLanguage("EN");
        card.setLocalPrice("");
        card.setCkPriceUsd("");
        card.setAction(ACTION_IN_STOCK);
        return card;
    }

    @PostMapping("/reservas/eliminar-grupo")
    public String deleteReservationGroup(
            @RequestParam("groupKey") String groupKey,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(groupKey)) {
            redirectAttributes.addFlashAttribute("error", "No se pudo identificar la reserva a eliminar.");
            return "redirect:/reservas";
        }

        try {
            List<CardReservation> reservations = inventoryService.getReservations()
                    .stream()
                    .filter(reservation -> groupKey.equals(customerReservationKey(reservation)))
                    .toList();

            if (reservations.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "No se encontro la reserva seleccionada.");
                return "redirect:/reservas";
            }

            List<InventoryCard> inventoryCards = inventoryService.getInventoryCards();
            int returnedToStock = 0;
            for (CardReservation reservation : reservations) {
                if (CardReservation.STATUS_RESERVED.equalsIgnoreCase(reservation.getStatus())) {
                    returnedToStock += returnReservedCardToStock(reservation, request, inventoryCards);
                }
            }

            inventoryService.deleteReservationRows(reservations.stream()
                    .map(CardReservation::getRowIndex)
                    .toList());
            refreshLatestUpdatesFromInventory();

            String client = blankToDash(reservations.get(0).getClient());
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Reserva de " + client + " eliminada."
                            + (returnedToStock > 0 ? " Se devolvieron " + returnedToStock + " carta(s) al stock." : "")
            );
        } catch (Exception e) {
            log.warn("No se pudo eliminar la reserva.", e);
            redirectAttributes.addFlashAttribute("error", "No se pudo eliminar la reserva: " + syncErrorMessage(e));
        }

        return "redirect:/reservas";
    }

    @PostMapping("/reservas/eliminar")
    public String deleteReservation(
            @RequestParam("reservationId") String reservationId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(reservationId)) {
            redirectAttributes.addFlashAttribute("error", "No se pudo identificar la carta a quitar.");
            return "redirect:/reservas";
        }

        try {
            List<CardReservation> reservations = inventoryService.getReservations();
            CardReservation reservation = consolidateDuplicateReservations(reservations)
                    .stream()
                    .filter(item -> reservationId.equalsIgnoreCase(item.getId()))
                    .findFirst()
                    .orElse(null);

            if (reservation == null) {
                redirectAttributes.addFlashAttribute("error", "No se encontro la carta seleccionada.");
                return "redirect:/reservas";
            }

            int returnedToStock = 0;
            if (CardReservation.STATUS_RESERVED.equalsIgnoreCase(reservation.getStatus())) {
                returnedToStock = returnReservedCardToStock(reservation, request);
            }

            inventoryService.deleteReservationRows(reservation.effectiveRowIndexes());
            refreshLatestUpdatesFromInventory();
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Carta quitada del pedido de " + blankToDash(reservation.getClient()) + "."
                            + (returnedToStock > 0 ? " Se devolvieron " + returnedToStock + " carta(s) al stock." : "")
            );
        } catch (Exception e) {
            log.warn("No se pudo quitar la carta del pedido.", e);
            redirectAttributes.addFlashAttribute("error", "No se pudo quitar la carta: " + syncErrorMessage(e));
        }

        return "redirect:/reservas";
    }

    @PostMapping("/reservas/entregar")
    public String deliverReservation(
            @RequestParam("reservationId") String reservationId,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(reservationId)) {
            redirectAttributes.addFlashAttribute("error", "No se pudo identificar la reserva a entregar.");
            return "redirect:/reservas";
        }

        try {
            List<CardReservation> reservations = inventoryService.getReservations();
            CardReservation reservation = reservations
                    .stream()
                    .filter(item -> reservationId.equalsIgnoreCase(item.getId()))
                    .findFirst()
                    .orElse(null);

            if (reservation == null) {
                redirectAttributes.addFlashAttribute("error", "No se encontro la reserva seleccionada.");
                return "redirect:/reservas";
            }
            reservation = consolidateDuplicateReservations(reservations)
                    .stream()
                    .filter(item -> reservationId.equalsIgnoreCase(item.getId()))
                    .findFirst()
                    .orElse(reservation);

            int requestedQuantity = reservationQuantity(reservation.getQuantity());
            InventoryCard card = findInventoryCardForReservation(reservation);

            if (card == null) {
                redirectAttributes.addFlashAttribute("error", "No se encontro stock compatible para entregar esta reserva.");
                return "redirect:/reservas";
            }

            int previousQuantity = quantity(card);
            int quantityToDeliver = Math.min(previousQuantity, requestedQuantity);
            if (quantityToDeliver <= 0) {
                redirectAttributes.addFlashAttribute("error", "No hay stock disponible para entregar esta reserva.");
                return "redirect:/reservas";
            }

            int newQuantity = previousQuantity - quantityToDeliver;
            card.setQuantity(String.valueOf(newQuantity));
            applyStockAction(card);
            inventoryService.updateStockState(card.getRowIndex(), card);

            if (movementsModuleEnabled(request)) {
                inventoryService.appendMovement(createMovement(
                        "SALIDA",
                        quantityToDeliver,
                        card,
                        previousQuantity,
                        newQuantity,
                        "Entrega reserva"
                ));
                LocalDateTime now = LocalDateTime.now(APP_ZONE);
                inventoryService.appendCashSale(
                        now.format(MOVEMENT_DATE_FORMAT),
                        now.format(MOVEMENT_TIME_FORMAT),
                        card,
                        quantityToDeliver
                );
            }

            int remainingReservationQuantity = requestedQuantity - quantityToDeliver;
            if (remainingReservationQuantity > 0) {
                inventoryService.updateReservationQuantity(reservation.getId(), String.valueOf(remainingReservationQuantity));
                inventoryService.deleteReservationRows(duplicateReservationRows(reservation));
            } else {
                inventoryService.deleteReservationRows(reservation.effectiveRowIndexes());
            }
            refreshLatestUpdateForCard(card);
            redirectAttributes.addFlashAttribute(
                    "success",
                    quantityToDeliver < requestedQuantity
                            ? "Entrega parcial registrada. Quedan " + remainingReservationQuantity + " "
                            + cardQuantityWord(remainingReservationQuantity)
                            + " pendiente para " + blankToDash(reservation.getClient()) + "."
                            : "Reserva de " + blankToDash(reservation.getClient()) + " entregada."
            );
        } catch (Exception e) {
            log.warn("No se pudo entregar la reserva.", e);
            redirectAttributes.addFlashAttribute("error", "No se pudo entregar la reserva: " + syncErrorMessage(e));
        }

        return "redirect:/reservas";
    }

    @PostMapping("/reservas/entregar-grupo")
    public String deliverReservationGroup(
            @RequestParam("groupKey") String groupKey,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(groupKey)) {
            redirectAttributes.addFlashAttribute("error", "No se pudo identificar la reserva a entregar.");
            return "redirect:/reservas";
        }

        try {
            List<CardReservation> reservations = inventoryService.getReservations()
                    .stream()
                    .filter(reservation -> groupKey.equals(customerReservationKey(reservation)))
                    .toList();
            reservations = consolidateDuplicateReservations(reservations);

            if (reservations.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "No se encontro la reserva seleccionada.");
                return "redirect:/reservas";
            }

            List<InventoryCard> inventoryCards = inventoryService.getInventoryCards();
            Map<Integer, InventoryCard> cardsByRow = new HashMap<>();
            for (InventoryCard card : inventoryCards) {
                cardsByRow.put(card.getRowIndex(), card);
            }

            List<Integer> deliveredRows = new ArrayList<>();
            int deliveredQuantity = 0;
            boolean logMovements = movementsModuleEnabled(request);

            for (CardReservation reservation : reservations) {
                InventoryCard card = findInventoryCardForReservation(reservation, inventoryCards);
                if (card == null) {
                    continue;
                }

                card = cardsByRow.getOrDefault(card.getRowIndex(), card);
                int requestedQuantity = reservationQuantity(reservation.getQuantity());
                int previousQuantity = quantity(card);
                int quantityToDeliver = Math.min(previousQuantity, requestedQuantity);
                if (quantityToDeliver <= 0) {
                    continue;
                }

                int newQuantity = previousQuantity - quantityToDeliver;
                card.setQuantity(String.valueOf(newQuantity));
                applyStockAction(card);
                inventoryService.updateStockState(card.getRowIndex(), card);

                if (logMovements) {
                    inventoryService.appendMovement(createMovement(
                            "SALIDA",
                            quantityToDeliver,
                            card,
                            previousQuantity,
                            newQuantity,
                            "Entrega reserva"
                    ));
                    LocalDateTime now = LocalDateTime.now(APP_ZONE);
                    inventoryService.appendCashSale(
                            now.format(MOVEMENT_DATE_FORMAT),
                            now.format(MOVEMENT_TIME_FORMAT),
                            card,
                            quantityToDeliver
                    );
                }

                refreshLatestUpdateForCard(card);
                int remainingReservationQuantity = requestedQuantity - quantityToDeliver;
                if (remainingReservationQuantity > 0) {
                    inventoryService.updateReservationQuantity(reservation.getId(), String.valueOf(remainingReservationQuantity));
                    deliveredRows.addAll(duplicateReservationRows(reservation));
                } else {
                    deliveredRows.addAll(reservation.effectiveRowIndexes());
                }
                deliveredQuantity += quantityToDeliver;
            }

            if (deliveredQuantity <= 0) {
                redirectAttributes.addFlashAttribute("error", "No hay cartas con stock suficiente para entregar en esta reserva.");
                return "redirect:/reservas";
            }

            if (!deliveredRows.isEmpty()) {
                inventoryService.deleteReservationRows(deliveredRows);
            }
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Se entregaron " + deliveredQuantity + " " + cardQuantityWord(deliveredQuantity)
                            + " de la reserva de " + blankToDash(reservations.get(0).getClient()) + "."
            );
        } catch (Exception e) {
            log.warn("No se pudo entregar la reserva completa.", e);
            redirectAttributes.addFlashAttribute("error", "No se pudo entregar la reserva: " + syncErrorMessage(e));
        }

        return "redirect:/reservas";
    }

    private List<Integer> duplicateReservationRows(CardReservation reservation) {
        return reservation.effectiveRowIndexes()
                .stream()
                .filter(rowIndex -> rowIndex != reservation.getRowIndex())
                .toList();
    }

    @PostMapping("/reservas")
    public String createReservation(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "setName", required = false) String setName,
            @RequestParam(name = "setCode", required = false) String setCode,
            @RequestParam(name = "collectorNumber", required = false) String collectorNumber,
            @RequestParam(name = "printing", required = false) String printing,
            @RequestParam(name = "quantity", required = false) String quantity,
            @RequestParam(name = "client", required = false) String client,
            @RequestParam(name = "phone", required = false) String phone,
            @RequestParam(name = "dni", required = false) String dni,
            @RequestParam(name = "pickupDate", required = false) String pickupDate,
            @RequestParam(name = "notes", required = false) String notes,
            RedirectAttributes redirectAttributes
    ) {
        if (isBlank(name) || isBlank(client) || isBlank(phone)) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Completa nombre de carta, cliente y telefono para guardar la reserva."
            );
            return "redirect:/reservas";
        }

        String contactError = reservationContactError(phone, dni);
        if (!isBlank(contactError)) {
            redirectAttributes.addFlashAttribute("error", contactError);
            return "redirect:/reservas";
        }

        try {
            saveReservation(status, name, setName, setCode, collectorNumber, printing, quantity, client, phone, dni, pickupDate, notes);
            redirectAttributes.addFlashAttribute("success", "Reserva guardada en el Sheet.");
        } catch (Exception e) {
            log.warn("No se pudo guardar la reserva.", e);
            redirectAttributes.addFlashAttribute("error", "No se pudo guardar la reserva: " + syncErrorMessage(e));
        }

        return "redirect:/reservas";
    }

    @PostMapping(value = "/reservas", headers = "X-Requested-With=fetch")
    @ResponseBody
    public ResponseEntity<?> createReservationFromSearch(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "setName", required = false) String setName,
            @RequestParam(name = "setCode", required = false) String setCode,
            @RequestParam(name = "collectorNumber", required = false) String collectorNumber,
            @RequestParam(name = "printing", required = false) String printing,
            @RequestParam(name = "quantity", required = false) String quantity,
            @RequestParam(name = "client", required = false) String client,
            @RequestParam(name = "phone", required = false) String phone,
            @RequestParam(name = "dni", required = false) String dni,
            @RequestParam(name = "pickupDate", required = false) String pickupDate,
            @RequestParam(name = "notes", required = false) String notes,
            @RequestParam(name = "rowIndex", required = false, defaultValue = "0") int rowIndex,
            @RequestParam(name = "removeFromStock", required = false, defaultValue = "false") boolean removeFromStock,
            HttpServletRequest request
    ) {
        if (isBlank(name) || isBlank(client) || isBlank(phone)) {
            return ResponseEntity.badRequest()
                    .body(new ApiMessage(false, "Completa carta, cliente y telefono para guardar la reserva."));
        }

        String contactError = reservationContactError(phone, dni);
        if (!isBlank(contactError)) {
            return ResponseEntity.badRequest()
                    .body(new ApiMessage(false, contactError));
        }

        try {
            int reservationQuantity = reservationQuantity(quantity);
            InventoryCard reservedCard = null;
            int previousQuantity = 0;
            int updatedQuantity = -1;
            String reservationStatus = removeFromStock ? CardReservation.STATUS_RESERVED : CardReservation.STATUS_WANTED;

            if (removeFromStock) {
                if (rowIndex <= 0) {
                    return ResponseEntity.badRequest()
                            .body(new ApiMessage(false, "No se pudo identificar la fila de inventario para retirar stock."));
                }

                reservedCard = findInventoryCardByRow(rowIndex);
                if (reservedCard == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiMessage(false, "No se encontro la carta en el inventario."));
                }

                previousQuantity = quantity(reservedCard);
                if (previousQuantity < reservationQuantity) {
                    return ResponseEntity.badRequest()
                            .body(new ApiMessage(false, "No hay stock suficiente para retirar esa cantidad."));
                }
            }

            saveReservation(reservationStatus, name, setName, setCode, collectorNumber, printing, String.valueOf(reservationQuantity), client, phone, dni, pickupDate, notes);

            if (removeFromStock && reservedCard != null) {
                updatedQuantity = previousQuantity;
                reservedCard.setQuantity(String.valueOf(updatedQuantity));
                reservedCard.setAction(previousQuantity <= reservationQuantity ? ACTION_RESERVED : ACTION_IN_STOCK);
                inventoryService.updateStockState(rowIndex, reservedCard);

                if (movementsModuleEnabled(request)) {
                    inventoryService.appendMovement(createMovement(
                            "ENTRADA",
                            reservationQuantity,
                            reservedCard,
                            previousQuantity,
                            updatedQuantity,
                            ACTION_RESERVED
                    ));
                }

                refreshLatestUpdateForCard(reservedCard);
            }

            return ResponseEntity.ok(new ReservationCreateResponse(
                    true,
                    removeFromStock ? "Pedido reservado en stock." : "Pedido guardado en Reservas.",
                    rowIndex,
                    updatedQuantity,
                    updatedQuantity >= 0 ? reservedCard.getAction() : "",
                    client.trim(),
                    digitsOnly(phone),
                    digitsOnly(dni)
            ));
        } catch (Exception e) {
            log.warn("No se pudo guardar la reserva desde busqueda.", e);
            return ResponseEntity.internalServerError()
                    .body(new ApiMessage(false, "No se pudo guardar la reserva: " + syncErrorMessage(e)));
        }
    }

    @GetMapping("/api/reservas/pendientes")
    @ResponseBody
    public ResponseEntity<List<PendingReservationView>> pendingReservationsForCard(
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "setName", required = false) String setName,
            @RequestParam(name = "setCode", required = false) String setCode,
            @RequestParam(name = "collectorNumber", required = false) String collectorNumber,
            @RequestParam(name = "printing", required = false) String printing
    ) {
        if (isBlank(name)) {
            return ResponseEntity.ok(List.of());
        }

        try {
            return ResponseEntity.ok(inventoryService.getReservations()
                    .stream()
                    .filter(reservation -> CardReservation.STATUS_WANTED.equalsIgnoreCase(reservation.getStatus()))
                    .filter(reservation -> matchesReservation(reservation, name, setName, setCode, collectorNumber, printing))
                    .sorted(Comparator.comparingInt(CardReservation::getRowIndex))
                    .map(reservation -> new PendingReservationView(
                            reservation.getId(),
                            reservation.getClient(),
                            reservation.getPhone(),
                            reservation.getQuantity(),
                            reservation.getPickupDate(),
                            reservation.getFormattedReservationDate(),
                            reservation.getNotes()
                    ))
                    .toList());
        } catch (Exception e) {
            log.warn("No se pudieron consultar reservas pendientes.", e);
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/api/reservas/clientes")
    @ResponseBody
    public ResponseEntity<List<ReservationClientView>> reservationClients() {
        try {
            Map<String, ReservationClientView> clientsByName = new LinkedHashMap<>();

            for (ReservationClient savedClient : inventoryService.getReservationClients()) {
                String client = savedClient.getClient();
                if (isBlank(client)) {
                    continue;
                }

                String key = normalizedCardText(client);
                clientsByName.putIfAbsent(key, new ReservationClientView(
                        client.trim(),
                        blankToEmpty(savedClient.getPhone()),
                        blankToEmpty(savedClient.getDni())
                ));
            }

            for (CardReservation reservation : inventoryService.getReservations()) {
                String client = reservation.getClient();
                if (isBlank(client)) {
                    continue;
                }

                String key = normalizedCardText(client);
                clientsByName.putIfAbsent(key, new ReservationClientView(
                        client.trim(),
                        blankToEmpty(reservation.getPhone()),
                        blankToEmpty(reservation.getDni())
                ));
            }

            return ResponseEntity.ok(new ArrayList<>(clientsByName.values()));
        } catch (Exception e) {
            log.warn("No se pudieron consultar clientes de reservas.", e);
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/reservas/separar")
    @ResponseBody
    public ResponseEntity<?> reservePendingReservation(
            @RequestParam String reservationId,
            @RequestParam(name = "sku", required = false) String sku,
            @RequestParam(name = "condition", required = false, defaultValue = "NM") String condition,
            @RequestParam(name = "rowIndex", required = false, defaultValue = "0") int rowIndex,
            HttpServletRequest request
    ) {
        try {
            var reservation = inventoryService.getReservations()
                    .stream()
                    .filter(item -> reservationId.equalsIgnoreCase(item.getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No se encontro la reserva seleccionada."));

            if (!CardReservation.STATUS_WANTED.equalsIgnoreCase(reservation.getStatus())) {
                return ResponseEntity.badRequest()
                        .body(new ApiMessage(false, "La reserva seleccionada ya no esta pendiente."));
            }

            int updatedRowIndex = rowIndex;
            int stockQuantity = 0;
            String action = ACTION_RESERVED;
            int reservationQuantity = reservationQuantity(reservation.getQuantity());
            InventoryCard reservedCard = null;
            int previousQuantity = 0;

            if (rowIndex > 0) {
                InventoryCard card = findInventoryCardByRow(rowIndex);
                if (card != null) {
                    reservedCard = card;
                    stockQuantity = quantity(card);
                    previousQuantity = stockQuantity;
                    if (stockQuantity <= 0) {
                        stockQuantity = reservationQuantity;
                        card.setQuantity(String.valueOf(stockQuantity));
                        card.setAction(ACTION_RESERVED);
                        inventoryService.updateStockState(rowIndex, card);
                        refreshLatestUpdateForCard(card);
                    } else if (stockQuantity <= reservationQuantity) {
                        card.setAction(ACTION_RESERVED);
                        inventoryService.updateStockState(rowIndex, card);
                        refreshLatestUpdateForCard(card);
                        action = ACTION_RESERVED;
                    } else {
                        action = stockActionForQuantity(stockQuantity);
                    }
                }
            } else if (!isBlank(sku)) {
                CardKingdomProduct product = findProductBySku(sku);
                if (product == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new ApiMessage(false, "No se encontro la carta en Card Kingdom."));
                }

                InventoryCard card = createInventoryCard(product, condition);
                card.setQuantity(String.valueOf(reservationQuantity));
                card.setAction(ACTION_RESERVED);
                updatedRowIndex = inventoryService.appendInventoryCard(card);
                card.setRowIndex(updatedRowIndex);
                reservedCard = card;
                stockQuantity = reservationQuantity;
                refreshLatestUpdateForCard(card);
            }

            inventoryService.updateReservationStatus(reservationId, CardReservation.STATUS_RESERVED);
            if (reservedCard != null && movementsModuleEnabled(request)) {
                inventoryService.appendMovement(createMovement(
                        "ENTRADA",
                        reservationQuantity,
                        reservedCard,
                        previousQuantity,
                        stockQuantity,
                        ACTION_RESERVED
                ));
            }

            return ResponseEntity.ok(new ReservationStockResponse(
                    true,
                    "Carta separada para el pedido de " + reservation.getClient() + ".",
                    updatedRowIndex,
                    stockQuantity,
                    action
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiMessage(false, e.getMessage()));
        } catch (Exception e) {
            log.warn("No se pudo separar la reserva.", e);
            return ResponseEntity.internalServerError()
                    .body(new ApiMessage(false, "No se pudo separar la reserva: " + syncErrorMessage(e)));
        }
    }

    private List<String> reservationStatuses() {
        return List.of(
                CardReservation.STATUS_IN_STOCK,
                CardReservation.STATUS_RESERVED,
                CardReservation.STATUS_WANTED
        );
    }

    private void saveReservation(
            String status,
            String name,
            String setName,
            String setCode,
            String collectorNumber,
            String printing,
            String quantity,
            String client,
            String phone,
            String dni,
            String pickupDate,
            String notes
    ) throws Exception {
        CardReservation reservation = new CardReservation();
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        reservation.setId("RSV-" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        reservation.setStatus(normalizedReservationStatus(status));
        reservation.setName(name.trim());
        reservation.setSetName(blankToEmpty(setName));
        reservation.setSetCode(blankToEmpty(setCode));
        reservation.setCollectorNumber(blankToEmpty(collectorNumber));
        reservation.setPrinting(blankToEmpty(printing));
        reservation.setQuantity(normalizedReservationQuantity(quantity));
        reservation.setClient(client.trim());
        reservation.setPhone(digitsOnly(phone));
        reservation.setDni(digitsOnly(dni));
        reservation.setReservationDate(now.format(MOVEMENT_DATE_TIME_FORMAT));
        reservation.setPickupDate(normalizedPickupDate(pickupDate));
        reservation.setPaymentDate("");
        reservation.setNotes(blankToEmpty(notes));

        inventoryService.upsertReservationClient(
                reservation.getClient(),
                reservation.getPhone(),
                reservation.getDni(),
                now.format(MOVEMENT_DATE_TIME_FORMAT)
        );

        CardReservation existingReservation = inventoryService.getReservations()
                .stream()
                .filter(existing -> duplicateReservationKey(existing).equals(duplicateReservationKey(reservation)))
                .findFirst()
                .orElse(null);

        if (existingReservation != null) {
            int updatedQuantity = reservationQuantity(existingReservation.getQuantity())
                    + reservationQuantity(reservation.getQuantity());
            inventoryService.updateReservationQuantity(existingReservation.getId(), String.valueOf(updatedQuantity));
            return;
        }

        inventoryService.appendReservation(reservation);
    }

    private String normalizedReservationStatus(String status) {
        if (status == null || status.isBlank()) {
            return CardReservation.STATUS_WANTED;
        }

        String normalized = normalizeStatusText(status);
        if (normalized.equals(normalizeStatusText(CardReservation.STATUS_IN_STOCK))
                || normalized.equals("CON STOCK")) {
            return CardReservation.STATUS_IN_STOCK;
        }

        if (normalized.equals(normalizeStatusText(CardReservation.STATUS_RESERVED))
                || normalized.equals("RESERVADA")) {
            return CardReservation.STATUS_RESERVED;
        }

        if (normalized.equals(normalizeStatusText(CardReservation.STATUS_WANTED))
                || normalized.equals("BUSCADA")
                || normalized.equals("ENCARGADA")
                || normalized.equals("SIN STOCK")) {
            return CardReservation.STATUS_WANTED;
        }

        return CardReservation.STATUS_WANTED;
    }

    private String normalizedReservationQuantity(String quantity) {
        return String.valueOf(reservationQuantity(quantity));
    }

    private int reservationQuantity(String quantity) {
        if (quantity == null || quantity.isBlank()) {
            return 1;
        }

        try {
            return Math.max(1, Integer.parseInt(quantity.trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private String reservationContactError(String phone, String dni) {
        String phoneDigits = digitsOnly(phone);
        if (!phoneDigits.equals(blankToEmpty(phone))) {
            return "El telefono solo puede tener numeros.";
        }

        if (phoneDigits.length() != 10 || !(phoneDigits.startsWith("11") || phoneDigits.startsWith("15"))) {
            return "El telefono debe tener 10 digitos y empezar con 11 o 15.";
        }

        String dniDigits = digitsOnly(dni);
        if (!dniDigits.equals(blankToEmpty(dni))) {
            return "El DNI solo puede tener numeros.";
        }

        if (!dniDigits.isBlank() && (dniDigits.length() < 7 || dniDigits.length() > 8)) {
            return "El DNI debe tener 7 u 8 digitos.";
        }

        return "";
    }

    private String digitsOnly(String value) {
        return value == null ? "" : value.trim().replaceAll("\\D", "");
    }

    private String normalizeStatusText(String status) {
        return status == null
                ? ""
                : status.trim()
                .replace("-", " ")
                .replace("_", " ")
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    private boolean isReservedStatus(String status) {
        return normalizeStatusText(status).equals(normalizeStatusText(ACTION_RESERVED))
                || normalizeStatusText(status).equals("RESERVADA");
    }

    private boolean isOutOfStockStatus(String status) {
        return normalizeStatusText(status).equals(normalizeStatusText(ACTION_OUT_OF_STOCK));
    }

    private String normalizedPickupDate(String pickupDate) {
        if (pickupDate == null || pickupDate.isBlank()) {
            return "A convenir";
        }

        return pickupDate.trim();
    }

    private boolean matchesReservation(
            CardReservation reservation,
            String name,
            String setName,
            String setCode,
            String collectorNumber,
            String printing
    ) {
        if (!normalizedCardText(reservation.getName()).equals(normalizedCardText(name))) {
            return false;
        }

        if (!isBlank(setName)
                && !isBlank(reservation.getSetName())
                && !normalizedCardText(reservation.getSetName()).equals(normalizedCardText(setName))) {
            return false;
        }

        if (!isBlank(setCode)
                && !isBlank(reservation.getSetCode())
                && !reservation.getSetCode().trim().equalsIgnoreCase(setCode.trim())) {
            return false;
        }

        if (!isBlank(collectorNumber)
                && !isBlank(reservation.getCollectorNumber())
                && !collectorNumber(reservation.getCollectorNumber()).equals(collectorNumber(collectorNumber))) {
            return false;
        }

        return isBlank(printing)
                || isBlank(reservation.getPrinting())
                || normalizedPrintingForReservation(reservation.getPrinting()).equals(normalizedPrintingForReservation(printing));
    }

    private String normalizedCardText(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String normalizedPrintingForReservation(String value) {
        String normalized = normalizedCardText(value);
        if (normalized.equals("no foil") || normalized.equals("non foil")) {
            return "nonfoil";
        }

        return normalized;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isMovementsUnlocked(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(MOVEMENTS_ACCESS_SESSION_KEY));
    }

    private boolean movementsModuleEnabled(HttpServletRequest request) {
        return request != null && isMovementsUnlocked(request.getSession(false));
    }

    private String movementAccessReturnPath(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath != null && !contextPath.isBlank() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }

        String queryString = request.getQueryString();
        return safeMovementAccessReturnPath(
                queryString == null || queryString.isBlank()
                        ? requestPath
                        : requestPath + "?" + queryString
        );
    }

    private String safeMovementAccessReturnPath(String returnTo) {
        if (returnTo == null || returnTo.isBlank()
                || returnTo.contains("\r") || returnTo.contains("\n")
                || returnTo.startsWith("//")) {
            return "/movimientos";
        }

        if ("/movimientos".equals(returnTo) || returnTo.startsWith("/movimientos?")) {
            return returnTo;
        }

        return "/movimientos";
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
                .filter(entry -> "VENTA".equalsIgnoreCase(entry.getType()))
                .mapToDouble(entry -> parseCashTotal(entry.getTotal()))
                .sum();
    }

    private double parseCashTotal(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            String normalized = value.replace("$", "").trim();

            if (normalized.contains(",") && normalized.contains(".")) {
                normalized = normalized.replace(".", "").replace(",", ".");
            } else if (normalized.contains(",")) {
                normalized = normalized.replace(",", ".");
            }

            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String formatCashTotal(double value) {
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
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim()
                .replace("$", "")
                .replace(" ", "");

        int lastDot = normalized.lastIndexOf('.');
        int lastComma = normalized.lastIndexOf(',');

        if (lastDot >= 0 && lastComma >= 0) {
            normalized = lastComma > lastDot
                    ? normalized.replace(".", "").replace(",", ".")
                    : normalized.replace(",", "");
        } else if (lastComma >= 0) {
            normalized = normalized.replace(".", "").replace(",", ".");
        }

        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<CashReportMonth> cashReportMonths(
            List<CashRegisterEntry> cashEntries,
            List<InventoryMovement> movements
    ) {
        Map<String, Map<String, CashReportCardAccumulator>> cardsByMonth = new LinkedHashMap<>();

        if (movements != null) {
            for (InventoryMovement movement : movements) {
                int movementQuantity = Math.abs(signedQuantity(movement));
                if (isReservationMovement(movement) && movementQuantity > 0) {
                    String monthKey = monthKey(movement.getDate());
                    CashReportCardAccumulator cardReport = cardsByMonth
                            .computeIfAbsent(monthKey, key -> new LinkedHashMap<>())
                            .computeIfAbsent(movementReportKey(movement), key -> new CashReportCardAccumulator());
                    cardReport.addReserved(movementQuantity);
                    continue;
                }

                int quantity = signedQuantity(movement);

                if (quantity == 0) {
                    continue;
                }

                String monthKey = monthKey(movement.getDate());
                CashReportCardAccumulator cardReport = cardsByMonth
                        .computeIfAbsent(monthKey, key -> new LinkedHashMap<>())
                        .computeIfAbsent(movementReportKey(movement), key -> new CashReportCardAccumulator());

                if (quantity > 0) {
                    cardReport.addEntry(quantity);
                } else {
                    cardReport.addSold(Math.abs(quantity));
                }
            }
        }

        if (cashEntries != null) {
            for (CashRegisterEntry entry : cashEntries) {
                if (!"VENTA".equalsIgnoreCase(entry.getType())) {
                    continue;
                }

                String monthKey = monthKey(entry.getDate());
                Map<String, CashReportCardAccumulator> monthCards = cardsByMonth.get(monthKey);
                if (monthCards == null) {
                    continue;
                }

                CashReportCardAccumulator cardReport = monthCards.get(cashEntryReportKey(entry));
                if (cardReport != null) {
                    cardReport.addSaleTotal(parseCashTotal(entry.getTotal()));
                }
            }
        }

        Map<String, CashReportAccumulator> reports = new LinkedHashMap<>();
        for (var monthEntry : cardsByMonth.entrySet()) {
            CashReportAccumulator report = new CashReportAccumulator();

            for (CashReportCardAccumulator cardReport : monthEntry.getValue().values()) {
                if (!cardReport.hasEntryAndSale()) {
                    continue;
                }

                report.addCard(cardReport);
            }

            reports.put(monthEntry.getKey(), report);
        }

        double maxSales = reports.values().stream()
                .mapToDouble(report -> report.salesTotal)
                .max()
                .orElse(0);

        int maxMovementQuantity = reports.values().stream()
                .mapToInt(report -> Math.max(Math.max(report.soldQuantity, report.enteredQuantity), report.reservedQuantity))
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

    private boolean isReservationMovement(InventoryMovement movement) {
        return movement != null && ACTION_RESERVED.equalsIgnoreCase(blankToEmpty(movement.getSource()));
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
            @RequestParam(name = "honorReservations", required = false, defaultValue = "false") boolean honorReservations,
            HttpServletRequest request,
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
            List<CardReservation> pendingReservations = honorReservations
                    ? inventoryService.getReservations()
                    .stream()
                    .filter(reservation -> CardReservation.STATUS_WANTED.equalsIgnoreCase(reservation.getStatus()))
                    .toList()
                    : List.of();
            List<String> reservationsToMarkReserved = new ArrayList<>();
            boolean logMovements = movementsModuleEnabled(request);
            int added = 0;
            int updated = 0;
            int reserved = 0;

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
                ImportReservationAllocation reservationAllocation = allocateImportReservations(
                        product,
                        quantity,
                        pendingReservations,
                        reservationsToMarkReserved
                );
                int stockQuantityToAdd = quantity - reservationAllocation.reservedQuantity();
                reserved += reservationAllocation.reservedQuantity();

                if (existingCard == null) {
                    InventoryCard newCard = createInventoryCard(product);
                    newCard.setQuantity(String.valueOf(quantity));
                    newCard.setAction(stockQuantityToAdd > 0 ? ACTION_IN_STOCK : ACTION_RESERVED);
                    cardsToAppend.add(newCard);
                    if (logMovements && stockQuantityToAdd > 0) {
                        movements.add(createMovement("ENTRADA", stockQuantityToAdd, newCard, 0, stockQuantityToAdd, "Importar lista"));
                    }
                    if (logMovements && reservationAllocation.reservedQuantity() > 0) {
                        movements.add(createMovement(
                                "ENTRADA",
                                reservationAllocation.reservedQuantity(),
                                newCard,
                                0,
                                quantity,
                                ACTION_RESERVED
                        ));
                    }
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
                existingCard.setAction(reservationAllocation.reservedQuantity() > 0 && stockQuantityToAdd <= 0
                        ? ACTION_RESERVED
                        : stockActionForQuantity(updatedQuantity));
                cardsToWrite.put(existingCard.getRowIndex(), existingCard);
                if (logMovements && stockQuantityToAdd > 0) {
                    movements.add(createMovement(
                            "ENTRADA",
                            stockQuantityToAdd,
                            existingCard,
                            updatedQuantity - quantity,
                            updatedQuantity,
                            "Importar lista"
                    ));
                }
                if (logMovements && reservationAllocation.reservedQuantity() > 0) {
                    movements.add(createMovement(
                            "ENTRADA",
                            reservationAllocation.reservedQuantity(),
                            existingCard,
                            previousQuantity,
                            updatedQuantity,
                            ACTION_RESERVED
                    ));
                }
                updated++;
            }

            inventoryService.updateQuantities(quantitiesByRow);
            inventoryService.updateInventoryRows(cardsToWrite);
            inventoryService.appendInventoryCards(cardsToAppend);
            for (String reservationId : reservationsToMarkReserved) {
                inventoryService.updateReservationStatus(reservationId, CardReservation.STATUS_RESERVED);
            }
            if (logMovements) {
                inventoryService.appendMovements(movements);
            }
            refreshLatestUpdatesFromInventory();

            redirectAttributes.addFlashAttribute(
                    "success",
                    added + " carta(s) nuevas agregadas, " + updated + " carta(s) existentes actualizadas"
                            + (reserved > 0 ? " y " + reserved + " carta(s) separadas para reservas." : ".")
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

                if (alternatives.isEmpty()) {
                    alternatives = createImportOptions(
                            findPossibleVariantProducts(priceList.getData(), parsedLine),
                            parsedLine,
                            inventoryIndex
                    );
                }

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

            if (shouldGroupImportAlternatives(parsedLine, products)) {
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
                        parsedLine.duplicateCount() > 0 ? importStatus(parsedLine) : "OTRA VERSION",
                        false,
                        createImportOptions(products, parsedLine, inventoryIndex)
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

    private boolean shouldGroupImportAlternatives(
            ParsedImportLine parsedLine,
            List<CardKingdomProduct> products
    ) {
        return parsedLine != null
                && parsedLine.setCode().isBlank()
                && parsedLine.collectorNumber().isBlank()
                && products != null
                && products.size() > 1;
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

    private ImportReservationAllocation allocateImportReservations(
            CardKingdomProduct product,
            int importedQuantity,
            List<CardReservation> pendingReservations,
            List<String> reservationsToMarkReserved
    ) {
        if (product == null || importedQuantity <= 0 || pendingReservations == null || pendingReservations.isEmpty()) {
            return new ImportReservationAllocation(0);
        }

        int remaining = importedQuantity;
        int reservedQuantity = 0;
        String setCode = setCode(product.getSku());
        String collectorNumber = collectorNumberForSheet(product.getSku());
        String printing = "true".equalsIgnoreCase(product.getFoil()) ? "Foil" : "No Foil";

        for (CardReservation reservation : pendingReservations) {
            if (remaining <= 0) {
                break;
            }

            if (reservationsToMarkReserved.contains(reservation.getId())) {
                continue;
            }

            if (!matchesReservation(
                    reservation,
                    product.getName(),
                    product.getEdition(),
                    setCode,
                    collectorNumber,
                    printing
            )) {
                continue;
            }

            int reservationQuantity = reservationQuantity(reservation.getQuantity());
            if (reservationQuantity > remaining) {
                continue;
            }

            reservationsToMarkReserved.add(reservation.getId());
            reservedQuantity += reservationQuantity;
            remaining -= reservationQuantity;
        }

        return new ImportReservationAllocation(reservedQuantity);
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

    private List<CardKingdomProduct> findPossibleVariantProducts(
            List<CardKingdomProduct> products,
            ParsedImportLine parsedLine
    ) {
        String requestedName = normalizeImportedName(parsedLine.name());
        if (requestedName.length() < 5 || products == null || products.isEmpty()) {
            return List.of();
        }

        return products.stream()
                .filter(product -> {
                    if (!parsedLine.setCode().isBlank()
                            && (product.getSku() == null
                            || !product.getSku().toLowerCase()
                            .startsWith(parsedLine.setCode().toLowerCase() + "-"))) {
                        return false;
                    }

                    if (!parsedLine.collectorNumber().isBlank()
                            && !importCollectorFromSku(product.getSku()).equals(parsedLine.collectorNumber())) {
                        return false;
                    }

                    String variation = normalizeImportedName(searchableVariationText(product.getVariation()));
                    String productName = normalizeImportedName(product.getName());
                    return normalizedImportNameMatches(variation, requestedName)
                            || normalizedImportNameMatches(productName, requestedName);
                })
                .distinct()
                .toList();
    }

    private boolean normalizedImportNameMatches(String productName, String requestedName) {
        return !productName.isBlank()
                && !requestedName.isBlank()
                && productName.equals(requestedName);
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
            indexImportedProduct(productsByName, product, searchableVariationText(product.getVariation()));
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

        for (String candidate : importVariationNameCandidates(name)) {
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

    private List<String> importVariationNameCandidates(String variation) {
        String normalizedVariation = normalizeImportedName(searchableVariationText(variation));
        if (normalizedVariation.isBlank()) {
            return List.of();
        }

        List<String> candidates = new ArrayList<>();
        addImportCandidate(candidates, normalizedVariation);
        return candidates;
    }

    private void addImportCandidate(List<String> candidates, String candidate) {
        String normalizedCandidate = normalizeImportedName(candidate);
        if (!normalizedCandidate.isBlank() && !candidates.contains(normalizedCandidate)) {
            candidates.add(normalizedCandidate);
        }
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
            pricingSettingsService.update(ckDollarRate, roundMultiple);
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
            Model model
    ) {
        try {
            pricingSettingsService.update(ckDollarRate, roundMultiple);
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

    private synchronized boolean synchronizeInventory(boolean refreshPriceList) throws Exception {
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

        for (var card : cards) {
            UpdateResult update = updateCard(card.getRowIndex(), card, priceList, cards);
            updates.add(update);

            if (card.getRowIndex() > 0 && update.writeRequired()) {
                cardsToWrite.put(card.getRowIndex(), card);
            }
        }

        inventoryService.updateInventoryRows(cardsToWrite);

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
            List<InventoryCard> inventoryCards
    ) throws Exception {
        String previousCkPrice = card.getCkPriceUsd();
        String previousLocalPrice = card.getLocalPrice();
        String previousAction = card.getAction();
        List<ReservationConditionStock> conditionStocks = conditionStocksForInventoryCard(card, inventoryCards);

        if (quantity(card) <= 0) {
            card.setAction(isReservedStatus(previousAction) ? ACTION_RESERVED : ACTION_OUT_OF_STOCK);

            return new UpdateResult(
                    card.getName(),
                    card.getSetName(),
                    card.getSetCode(),
                    card.getCollectorNumber(),
                    card.getPrinting(),
                    card.getLocalPrice(),
                    card.getCkPriceUsd(),
                    card.getAction(),
                    displayCondition(card.getCondition()),
                    conditionStocks,
                    rowIndex,
                    quantity(card),
                    !Objects.equals(previousAction, card.getAction())
            );
        }

        var product = cardKingdomApiService.findProduct(card, priceList);

        if (product == null) {
            card.setCkPriceUsd("");
            card.setLocalPrice("");
            applyStockAction(card, previousAction);

            return new UpdateResult(
                    card.getName(),
                    card.getSetName(),
                    card.getSetCode(),
                    card.getCollectorNumber(),
                    card.getPrinting(),
                    card.getLocalPrice(),
                    card.getCkPriceUsd(),
                    card.getAction(),
                    displayCondition(card.getCondition()),
                    conditionStocks,
                    rowIndex,
                    quantity(card),
                    changed(previousCkPrice, previousLocalPrice, previousAction, card)
            );
        }

        Double ckPrice = priceComparisonService.getBestConditionPrice(product, card);

        if (ckPrice == null) {
            card.setCkPriceUsd("");
            card.setLocalPrice("");
            applyStockAction(card, previousAction);

            return new UpdateResult(
                    card.getName(),
                    card.getSetName(),
                    card.getSetCode(),
                    card.getCollectorNumber(),
                    card.getPrinting(),
                    card.getLocalPrice(),
                    "",
                    card.getAction(),
                    displayCondition(card.getCondition()),
                    conditionStocks,
                    rowIndex,
                    quantity(card),
                    changed(previousCkPrice, previousLocalPrice, previousAction, card)
            );
        }

        String localPrice = String.format("%.0f", priceComparisonService.calculateLocalPrice(ckPrice));
        card.setCkPriceUsd(String.format(java.util.Locale.US, "%.2f", ckPrice));
        card.setLocalPrice(localPrice);
        applyStockAction(card, previousAction);

        if (!changed(previousCkPrice, previousLocalPrice, previousAction, card)) {
            return new UpdateResult(
                    card.getName(),
                    card.getSetName(),
                    card.getSetCode(),
                    card.getCollectorNumber(),
                    card.getPrinting(),
                    card.getLocalPrice(),
                    card.getCkPriceUsd(),
                    card.getAction(),
                    displayCondition(card.getCondition()),
                    conditionStocks,
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
                card.getPrinting(),
                card.getLocalPrice(),
                card.getCkPriceUsd(),
                card.getAction(),
                displayCondition(card.getCondition()),
                conditionStocks,
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
            List<InventoryCard> inventoryCards
    ) {
        String collectorNumber = collectorNumber(product.getSku());
        boolean foil = "true".equalsIgnoreCase(product.getFoil());
        var matches = inventoryCards.stream()
                .filter(card -> matchesInventoryCard(card, product, collectorNumber, foil))
                .toList();

        int nmStockQuantity = stockQuantityForCondition(matches, "NM");
        int exStockQuantity = stockQuantityForCondition(matches, "EX");
        int vgStockQuantity = stockQuantityForCondition(matches, "VG");
        int gStockQuantity = stockQuantityForCondition(matches, "G");
        int nmRowIndex = rowIndexForCondition(matches, "NM");
        int exRowIndex = rowIndexForCondition(matches, "EX");
        int vgRowIndex = rowIndexForCondition(matches, "VG");
        int gRowIndex = rowIndexForCondition(matches, "G");
        List<ReservationConditionStock> conditionStocks = conditionStocksForInventoryCards(matches);

        String nmPrice = product.getConditionValues() == null
                ? ""
                : product.getConditionValues().getNmPrice();
        String exPrice = product.getConditionValues() == null
                ? ""
                : product.getConditionValues().getExPrice();
        String vgPrice = product.getConditionValues() == null
                ? ""
                : product.getConditionValues().getVgPrice();
        String gPrice = product.getConditionValues() == null
                ? ""
                : product.getConditionValues().getGPrice();
        String selectedCondition = matches.stream()
                .map(InventoryCard::getCondition)
                .filter(condition -> condition != null && !condition.isBlank())
                .findFirst()
                .map(this::displayCondition)
                .orElse("NM");
        int stockQuantity = conditionStockQuantity(
                selectedCondition,
                nmStockQuantity,
                exStockQuantity,
                vgStockQuantity,
                gStockQuantity
        );
        int rowIndex = conditionRowIndex(
                selectedCondition,
                nmRowIndex,
                exRowIndex,
                vgRowIndex,
                gRowIndex
        );
        String selectedCkPrice = conditionPrice(selectedCondition, nmPrice, exPrice, vgPrice, gPrice);
        String nmLocalPrice = localPriceFromCkPrice(nmPrice);
        String exLocalPrice = localPriceFromCkPrice(exPrice);
        String vgLocalPrice = localPriceFromCkPrice(vgPrice);
        String gLocalPrice = localPriceFromCkPrice(gPrice);
        String localPrice = localPriceFromCkPrice(selectedCkPrice);

        return new SearchResult(
                product.getName(),
                product.getEdition(),
                product.getSku(),
                setCode(product.getSku()),
                collectorNumberForSheet(product.getSku()),
                product.getVariation(),
                foil ? "Foil" : "No Foil",
                selectedCondition,
                nmPrice,
                exPrice,
                vgPrice,
                gPrice,
                nmLocalPrice,
                exLocalPrice,
                vgLocalPrice,
                gLocalPrice,
                nmStockQuantity,
                exStockQuantity,
                vgStockQuantity,
                gStockQuantity,
                nmRowIndex,
                exRowIndex,
                vgRowIndex,
                gRowIndex,
                conditionStocks,
                localPrice,
                stockQuantity,
                rowIndex
        );
    }

    private int stockQuantityForCondition(List<InventoryCard> cards, String condition) {
        return cards.stream()
                .filter(card -> displayCondition(card.getCondition()).equals(condition))
                .mapToInt(this::quantity)
                .sum();
    }

    private List<ReservationConditionStock> conditionStocksForInventoryCard(
            InventoryCard card,
            List<InventoryCard> inventoryCards
    ) {
        if (card == null || inventoryCards == null || inventoryCards.isEmpty()) {
            return List.of();
        }

        List<InventoryCard> matches = inventoryCards.stream()
                .filter(candidate -> sameInventoryCardFamily(card, candidate))
                .toList();
        return conditionStocksForInventoryCards(matches);
    }

    private List<ReservationConditionStock> conditionStocksForInventoryCards(List<InventoryCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }

        List<ReservationConditionStock> stocks = new ArrayList<>();
        for (String condition : List.of("NM", "EX", "VG", "G")) {
            List<InventoryCard> matches = cards.stream()
                    .filter(card -> displayCondition(card.getCondition()).equals(condition))
                    .toList();
            int quantity = matches.stream().mapToInt(this::quantity).sum();
            int reservedQuantity = matches.stream()
                    .filter(card -> isReservedStatus(card.getAction()))
                    .mapToInt(this::quantity)
                    .sum();

            if (quantity <= 0) {
                continue;
            }

            stocks.add(new ReservationConditionStock(
                    condition,
                    quantity,
                    Math.max(quantity - reservedQuantity, 0),
                    reservedQuantity,
                    reservedQuantity > 0 && reservedQuantity >= quantity ? ACTION_RESERVED : ACTION_IN_STOCK
            ));
        }

        long stockedConditionCount = stocks.stream()
                .filter(stock -> stock.quantity() > 0)
                .map(ReservationConditionStock::condition)
                .distinct()
                .count();

        return stockedConditionCount > 1 ? stocks : List.of();
    }

    private boolean sameInventoryCardFamily(InventoryCard first, InventoryCard second) {
        return normalizedCardText(first.getName()).equals(normalizedCardText(second.getName()))
                && normalizedCardText(first.getSetName()).equals(normalizedCardText(second.getSetName()))
                && normalizedCardText(first.getSetCode()).equals(normalizedCardText(second.getSetCode()))
                && collectorNumber(first.getCollectorNumber()).equals(collectorNumber(second.getCollectorNumber()))
                && normalizedPrintingForReservation(first.getPrinting()).equals(normalizedPrintingForReservation(second.getPrinting()));
    }

    private int rowIndexForCondition(List<InventoryCard> cards, String condition) {
        return cards.stream()
                .filter(card -> displayCondition(card.getCondition()).equals(condition))
                .mapToInt(InventoryCard::getRowIndex)
                .filter(index -> index > 0)
                .findFirst()
                .orElse(0);
    }

    private int conditionStockQuantity(
            String condition,
            int nmStockQuantity,
            int exStockQuantity,
            int vgStockQuantity,
            int gStockQuantity
    ) {
        return switch (displayCondition(condition)) {
            case "EX" -> exStockQuantity;
            case "VG" -> vgStockQuantity;
            case "G" -> gStockQuantity;
            default -> nmStockQuantity;
        };
    }

    private int conditionRowIndex(
            String condition,
            int nmRowIndex,
            int exRowIndex,
            int vgRowIndex,
            int gRowIndex
    ) {
        return switch (displayCondition(condition)) {
            case "EX" -> exRowIndex;
            case "VG" -> vgRowIndex;
            case "G" -> gRowIndex;
            default -> nmRowIndex;
        };
    }

    private String conditionPrice(
            String condition,
            String nmPrice,
            String exPrice,
            String vgPrice,
            String gPrice
    ) {
        return switch (displayCondition(condition)) {
            case "EX" -> exPrice;
            case "VG" -> vgPrice;
            case "G" -> gPrice;
            default -> nmPrice;
        };
    }

    private String displayCondition(String condition) {
        if (condition == null || condition.isBlank()) {
            return "NM";
        }

        String normalized = condition.toUpperCase().replaceAll("[^A-Z0-9]+", "");
        return switch (normalized) {
            case "LP", "EX", "EXCELLENT", "LIGHTLYPLAYED" -> "EX";
            case "VG", "VERYGOOD", "MP", "MODERATELYPLAYED" -> "VG";
            case "G", "GOOD", "HP", "HEAVILYPLAYED", "DAMAGED", "DMG" -> "G";
            default -> "NM";
        };
    }

    private String localPriceFromCkPrice(String ckPrice) {
        Double parsedCkPrice = parsePriceValue(ckPrice);
        if (parsedCkPrice == null) {
            return "";
        }

        return String.format("%.0f", priceComparisonService.calculateLocalPrice(parsedCkPrice));
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
            int quantity = Integer.parseInt(card.getQuantity().trim());
            return Math.max(quantity, 0);
        } catch (NumberFormatException e) {
            return 0;
        }
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
        String movementSource = isBlank(source)
                ? stockAction("SALIDA".equalsIgnoreCase(type) ? -quantity : quantity)
                : source;

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
                movementSource
        );
    }

    private void addBaseModel(Model model, String query) {
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("updates", List.of());
        model.addAttribute("updatePerformed", false);
        model.addAttribute("updatedCount", 0);
        model.addAttribute("ckDollarRate", pricingSettingsService.getCkDollarRate());
        model.addAttribute("roundMultiple", pricingSettingsService.getRoundMultiple());
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("hasStoreLogo", storeSettingsService.hasLogo());
        model.addAttribute("sheetEditorEmail", inventoryService.getServiceAccountEmail());
        model.addAttribute("sheetConfigured", storeSettingsService.hasSpreadsheetConfigured());
        model.addAttribute("credentialsConfigured", inventoryService.hasCredentialsConfigured());
        model.addAttribute("credentialsPath", inventoryService.getConfiguredCredentialsPath());
        model.addAttribute("localCredentialsConfigured", storeSettingsService.hasGoogleCredentials());
        model.addAttribute("priceListLastUpdated", formattedPriceListLastUpdated());
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
        model.addAttribute("updates", groupedLatestUpdates());
        model.addAttribute("updatePerformed", !latestUpdates.isEmpty());
        model.addAttribute("updatedCount", latestUpdatedCount);
        model.addAttribute("priceListLastUpdated", formattedPriceListLastUpdated());
        model.addAttribute("pendingReservationQuantities", pendingReservationQuantitiesSafely());

        if (latestUpdates.isEmpty()) {
            return;
        }
    }

    private List<UpdateResult> groupedLatestUpdates() {
        if (latestUpdates.isEmpty()) {
            return latestUpdates;
        }

        Map<String, UpdateResult> grouped = new LinkedHashMap<>();
        for (UpdateResult update : latestUpdates) {
            grouped.merge(update.reservationKey(), update, this::preferredUpdateResult);
        }

        return new ArrayList<>(grouped.values());
    }

    private UpdateResult preferredUpdateResult(UpdateResult current, UpdateResult candidate) {
        int currentTotalStock = current.displayStockQuantity();
        int candidateTotalStock = candidate.displayStockQuantity();

        if (currentTotalStock <= 0 && candidateTotalStock > 0) {
            return candidate;
        }

        if ((current.conditionStocks() == null || current.conditionStocks().isEmpty())
                && candidate.conditionStocks() != null
                && !candidate.conditionStocks().isEmpty()) {
            return candidate;
        }

        if (isOutOfStockStatus(current.displayAction()) && !isOutOfStockStatus(candidate.displayAction())) {
            return candidate;
        }

        return current;
    }

    private Map<String, PendingReservationInfo> pendingReservationQuantitiesSafely() {
        try {
            return pendingReservationQuantities();
        } catch (Exception e) {
            log.warn("No se pudieron cargar pedidos pendientes para la tabla.", e);
            return Map.of();
        }
    }

    private Map<String, PendingReservationInfo> pendingReservationQuantities() throws Exception {
        Map<String, PendingReservationInfo> quantities = new HashMap<>();
        for (CardReservation reservation : inventoryService.getReservations()) {
            if (!CardReservation.STATUS_WANTED.equalsIgnoreCase(reservation.getStatus())) {
                continue;
            }

            String key = reservationLookupKey(
                    reservation.getName(),
                    reservation.getSetName(),
                    reservation.getSetCode(),
                    reservation.getCollectorNumber(),
                    reservation.getPrinting()
            );
            PendingReservationInfo info = quantities.computeIfAbsent(key, unused -> new PendingReservationInfo());
            info.add(reservation.getClient(), reservationQuantity(reservation.getQuantity()));
        }

        return quantities;
    }

    private String formattedPriceListLastUpdated() {
        return cardKingdomApiService.getPriceListLastUpdated()
                .map(updated -> PRICE_LIST_UPDATED_FORMAT.format(updated.atZone(APP_ZONE)))
                .orElse("");
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

    private void removeLatestUpdateForDeletedRow(int deletedRowIndex) {
        if (latestUpdates.isEmpty()) {
            return;
        }

        List<UpdateResult> refreshed = new ArrayList<>();

        for (UpdateResult update : latestUpdates) {
            if (update.rowIndex() == deletedRowIndex) {
                continue;
            }

            if (update.rowIndex() > deletedRowIndex) {
                refreshed.add(new UpdateResult(
                        update.name(),
                        update.edition(),
                        update.setCode(),
                        update.collectorNumber(),
                        update.printing(),
                        update.localPrice(),
                        update.ckPriceUsd(),
                        update.action(),
                        update.condition(),
                        update.conditionStocks(),
                        update.rowIndex() - 1,
                        update.stockQuantity(),
                        update.writeRequired()
                ));
            } else {
                refreshed.add(update);
            }
        }

        latestUpdates = List.copyOf(refreshed);
        latestUpdatedCount = refreshed.stream()
                .filter(UpdateResult::writeRequired)
                .count();
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
                card.getPrinting(),
                card.getLocalPrice(),
                card.getCkPriceUsd(),
                displayStockAction(card),
                displayCondition(card.getCondition()),
                conditionStocksForInventoryCards(List.of(card)),
                card.getRowIndex(),
                quantity(card),
                false
        );
    }

    private void applyStockAction(InventoryCard card) {
        applyStockAction(card, card.getAction());
    }

    private void applyStockAction(InventoryCard card, String previousAction) {
        card.setAction(isReservedStatus(previousAction) && quantity(card) > 0
                ? ACTION_RESERVED
                : stockActionForQuantity(quantity(card)));
    }

    private String displayStockAction(InventoryCard card) {
        int quantity = quantity(card);
        if (isReservedStatus(card.getAction()) && quantity > 0) {
            return ACTION_RESERVED;
        }

        return stockActionForQuantity(quantity);
    }

    private String stockActionForQuantity(int quantity) {
        return quantity <= 0 ? ACTION_OUT_OF_STOCK : ACTION_IN_STOCK;
    }

    public record SearchResult(
            String name,
            String edition,
            String sku,
            String setCode,
            String collectorNumber,
            String variation,
            String printing,
            String selectedCondition,
            String nmPrice,
            String exPrice,
            String vgPrice,
            String gPrice,
            String nmLocalPrice,
            String exLocalPrice,
            String vgLocalPrice,
            String gLocalPrice,
            int nmStockQuantity,
            int exStockQuantity,
            int vgStockQuantity,
            int gStockQuantity,
            int nmRowIndex,
            int exRowIndex,
            int vgRowIndex,
            int gRowIndex,
            List<ReservationConditionStock> conditionStocks,
            String localPrice,
            int stockQuantity,
            int rowIndex
    ) {
        public String formattedNmPrice() {
            return formatUsdPrice(nmPrice);
        }

        public String formattedExPrice() {
            return formatUsdPrice(exPrice);
        }

        public String formattedVgPrice() {
            return formatUsdPrice(vgPrice);
        }

        public String formattedGPrice() {
            return formatUsdPrice(gPrice);
        }

        public String formattedSelectedCkPrice() {
            return formatUsdPrice(switch (selectedCondition) {
                case "EX" -> exPrice;
                case "VG" -> vgPrice;
                case "G" -> gPrice;
                default -> nmPrice;
            });
        }

        public String formattedLocalPrice() {
            return formatLocalPrice(localPrice);
        }

        public String formattedNmLocalPrice() {
            return formatLocalPrice(nmLocalPrice);
        }

        public String formattedExLocalPrice() {
            return formatLocalPrice(exLocalPrice);
        }

        public String formattedVgLocalPrice() {
            return formatLocalPrice(vgLocalPrice);
        }

        public String formattedGLocalPrice() {
            return formatLocalPrice(gLocalPrice);
        }

        public int displayStockQuantity() {
            if (conditionStocks == null || conditionStocks.isEmpty()) {
                return stockQuantity;
            }

            return conditionStocks.stream()
                    .mapToInt(ReservationConditionStock::quantity)
                    .sum();
        }

        public String reservationKey() {
            return reservationLookupKey(name, edition, setCode, collectorNumber, printing);
        }
    }

    public record CardSuggestion(
            String name,
            String variation
    ) {
    }

    private record SuggestionCandidate(
            String name,
            String variation,
            String normalizedName,
            String normalizedVariation,
            int priority
    ) {
    }

    private record SearchFields(
            String nameQuery,
            String setFilter,
            String numberFilter
    ) {
    }

    private record SuggestionIndex(
            int identity,
            int size,
            List<SuggestionCandidate> candidates,
            ConcurrentHashMap<String, List<CardSuggestion>> cache
    ) {
        List<CardSuggestion> suggestionsFor(String normalizedQuery) {
            return cache.computeIfAbsent(normalizedQuery, this::buildSuggestions);
        }

        private List<CardSuggestion> buildSuggestions(String normalizedQuery) {
            Map<String, CardSuggestion> suggestions = new LinkedHashMap<>();

            var rankedCandidates = candidates.stream()
                    .filter(candidate -> suggestionScore(candidate, normalizedQuery) >= 0)
                    .sorted(Comparator
                            .comparingInt((SuggestionCandidate candidate) -> suggestionScore(candidate, normalizedQuery))
                            .thenComparingInt(SuggestionCandidate::priority)
                            .thenComparing(SuggestionCandidate::name))
                    .toList();

            for (SuggestionCandidate candidate : rankedCandidates) {
                if (suggestions.size() >= 8) {
                    break;
                }

                suggestions.putIfAbsent(candidate.name().toLowerCase(), new CardSuggestion(
                        candidate.name(),
                        candidate.variation()
                ));
            }

            return List.copyOf(suggestions.values());
        }

        private int suggestionScore(SuggestionCandidate candidate, String normalizedQuery) {
            if (candidate.normalizedName().equals(normalizedQuery)) {
                return 0;
            }

            if (candidate.normalizedName().startsWith(normalizedQuery)) {
                return 1;
            }

            if (wordStartsWith(candidate.normalizedName(), normalizedQuery)) {
                return 2;
            }

            if (candidate.normalizedVariation().startsWith(normalizedQuery)) {
                return 3;
            }

            if (wordStartsWith(candidate.normalizedVariation(), normalizedQuery)) {
                return 4;
            }

            if (candidate.normalizedName().contains(normalizedQuery)) {
                return 5;
            }

            if (candidate.normalizedVariation().contains(normalizedQuery)) {
                return 6;
            }

            for (String faceName : normalizedQuery.split("/")) {
                String normalizedFace = faceName.trim();

                if (!normalizedFace.isBlank()
                        && (candidate.normalizedName().contains(normalizedFace)
                        || candidate.normalizedVariation().contains(normalizedFace))) {
                    return 7;
                }
            }

            return -1;
        }

        private boolean wordStartsWith(String value, String normalizedQuery) {
            return value.contains(" " + normalizedQuery)
                    || value.contains("/" + normalizedQuery);
        }
    }

    public record UpdateResult(
            String name,
            String edition,
            String setCode,
            String collectorNumber,
            String printing,
            String localPrice,
            String ckPriceUsd,
            String action,
            String condition,
            List<ReservationConditionStock> conditionStocks,
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

        public int displayStockQuantity() {
            if (conditionStocks == null || conditionStocks.isEmpty()) {
                return stockQuantity;
            }

            return conditionStocks.stream()
                    .mapToInt(ReservationConditionStock::quantity)
                    .sum();
        }

        public String displayAction() {
            if (conditionStocks != null && !conditionStocks.isEmpty()) {
                int totalStock = conditionStocks.stream()
                        .mapToInt(ReservationConditionStock::quantity)
                        .sum();
                int reservedStock = conditionStocks.stream()
                        .mapToInt(ReservationConditionStock::reservedQuantity)
                        .sum();

                if (totalStock <= 0) {
                    return ACTION_OUT_OF_STOCK;
                }

                if (reservedStock > 0 && reservedStock >= totalStock) {
                    return ACTION_RESERVED;
                }

                return ACTION_IN_STOCK;
            }

            if (action != null && !action.isBlank()) {
                return action;
            }

            return stockQuantity > 0 ? ACTION_IN_STOCK : ACTION_OUT_OF_STOCK;
        }

        public String displayActionClass() {
            return displayAction().toLowerCase(Locale.ROOT).replace(" ", "-");
        }

        public String reservationKey() {
            return reservationLookupKey(name, edition, setCode, collectorNumber, printing);
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
            int reservedQuantity,
            int balanceQuantity,
            String salesWidth,
            String soldWidth,
            String enteredWidth,
            String reservedWidth
    ) {
    }

    private class CashReportAccumulator {
        private double salesTotal;
        private int soldQuantity;
        private int enteredQuantity;
        private int reservedQuantity;

        void addCard(CashReportCardAccumulator cardReport) {
            salesTotal += cardReport.salesTotal;
            soldQuantity += cardReport.soldQuantity;
            enteredQuantity += cardReport.enteredQuantity;
            reservedQuantity += cardReport.reservedQuantity;
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
                    reservedQuantity,
                    enteredQuantity - soldQuantity,
                    percent(salesTotal, maxSales),
                    percent(soldQuantity, maxMovementQuantity),
                    percent(enteredQuantity, maxMovementQuantity),
                    percent(reservedQuantity, maxMovementQuantity)
            );
        }
    }

    private static class CashReportCardAccumulator {
        private double salesTotal;
        private int soldQuantity;
        private int enteredQuantity;
        private int reservedQuantity;

        void addSaleTotal(double total) {
            salesTotal += total;
        }

        void addSold(int quantity) {
            soldQuantity += quantity;
        }

        void addEntry(int quantity) {
            enteredQuantity += quantity;
        }

        void addReserved(int quantity) {
            reservedQuantity += quantity;
        }

        boolean hasEntryAndSale() {
            return soldQuantity > 0 || enteredQuantity > 0 || reservedQuantity > 0 || salesTotal > 0;
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

    private record ImportReservationAllocation(int reservedQuantity) {
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
            @RequestParam int rowIndex,
            @RequestParam int change,
            @RequestParam(defaultValue = "false") boolean deleteWhenZero,
            HttpServletRequest request
    ) {
        try {
            InventoryCard card = findInventoryCardByRow(rowIndex);
            if (card == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiMessage(false, "No se encontro la carta en el inventario."));
            }

            int previousQuantity = quantity(card);

            if (previousQuantity <= 0 && change < 0) {
                if (!deleteWhenZero) {
                    return ResponseEntity.badRequest()
                            .body(new ApiMessage(false, "La carta ya esta en 0. Confirma el borrado para eliminarla del Sheet."));
                }

                inventoryService.deleteInventoryRow(rowIndex);
                removeLatestUpdateForDeletedRow(rowIndex);

                return ResponseEntity.ok(new StockDeleteResponse(
                        rowIndex,
                        true,
                        "Carta eliminada del Sheet"
                ));
            }

            int newQuantity = Math.max(previousQuantity + change, 0);

            if (previousQuantity != newQuantity) {
                card.setQuantity(String.valueOf(newQuantity));
                applyStockAction(card);
                inventoryService.updateStockState(rowIndex, card);

                if (movementsModuleEnabled(request)) {
                    inventoryService.appendMovement(createMovement(
                            change > 0 ? "ENTRADA" : "SALIDA",
                            Math.abs(newQuantity - previousQuantity),
                            card,
                            previousQuantity,
                            newQuantity,
                            change > 0 ? "Agregado al stock" : "Unidad vendida"
                    ));

                    if (change < 0) {
                        LocalDateTime now = LocalDateTime.now(APP_ZONE);
                        inventoryService.appendCashSale(
                                now.format(MOVEMENT_DATE_FORMAT),
                                now.format(MOVEMENT_TIME_FORMAT),
                                card,
                                Math.abs(newQuantity - previousQuantity)
                        );
                    }
                }

                refreshLatestUpdateForCard(card);
            }

            return ResponseEntity.ok(new StockUpdateResponse(
                    rowIndex,
                    newQuantity,
                    displayStockAction(card),
                    change > 0 ? "Unidad agregada" : "Unidad vendida"
            ));
        } catch (Exception e) {
            log.warn("No se pudo actualizar la cantidad de inventario.", e);
            return ResponseEntity.internalServerError()
                    .body(new ApiMessage(false, "No se pudo actualizar el stock: " + e.getMessage()));
        }
    }

    @PostMapping("/inventory/cards")
    @ResponseBody
    public ResponseEntity<?> addInventoryCard(
            @RequestParam String sku,
            @RequestParam(name = "condition", required = false, defaultValue = "NM") String condition,
            HttpServletRequest request
    ) {
        try {
            CardKingdomProduct product = findProductBySku(sku);

            if (product == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiMessage(false, "No se encontro la carta en Card Kingdom."));
            }

            InventoryCard card = createInventoryCard(product, condition);
            int rowIndex = inventoryService.appendInventoryCard(card);
            card.setRowIndex(rowIndex);
            if (movementsModuleEnabled(request)) {
                inventoryService.appendMovement(createMovement("ENTRADA", 1, card, 0, 1, "Busqueda"));
            }
            refreshLatestUpdateForCard(card);

            return ResponseEntity.ok(new StockCreateResponse(rowIndex));
        } catch (Exception e) {
            log.warn("No se pudo agregar la carta al inventario.", e);
            return ResponseEntity.internalServerError()
                    .body(new ApiMessage(false, "No se pudo agregar la carta al inventario: " + e.getMessage()));
        }
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
        return createInventoryCard(product, "NM");
    }

    private InventoryCard createInventoryCard(CardKingdomProduct product, String condition) {
        InventoryCard card = new InventoryCard();
        card.setQuantity("1");
        card.setName(product.getName());
        card.setSetCode(setCode(product.getSku()));
        card.setSetName(product.getEdition());
        card.setCollectorNumber(collectorNumberForSheet(product.getSku()));
        card.setCondition(displayCondition(condition));
        card.setPrinting("true".equalsIgnoreCase(product.getFoil()) ? "foil" : "nonfoil");
        card.setLanguage("EN");
        Double ckPrice = priceComparisonService.getBestConditionPrice(product, card);

        if (ckPrice == null) {
            card.setLocalPrice("");
            card.setCkPriceUsd("");
        } else {
            String localPrice = String.format("%.0f", priceComparisonService.calculateLocalPrice(ckPrice));
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

    public record StockCreateResponse(int rowIndex) {
    }

    public record ApiMessage(boolean success, String message) {
    }

    public record ReservationCreateResponse(
            boolean success,
            String message,
            int rowIndex,
            int stockQuantity,
            String action,
            String client,
            String phone,
            String dni
    ) {
    }

    public record PendingReservationView(
            String id,
            String client,
            String phone,
            String quantity,
            String pickupDate,
            String reservationDate,
            String notes
    ) {
    }

    public record ReservationClientView(
            String client,
            String phone,
            String dni
    ) {
    }

    public record ReservationStockResponse(
            boolean success,
            String message,
            int rowIndex,
            int stockQuantity,
            String action
    ) {
    }

    public record ReservationGroupView(
            String key,
            String client,
            String phone,
            String dni,
            String status,
            int lineCount,
            int totalQuantity,
            int availableQuantity,
            String statusLabel,
            String formattedTotalPrice,
            String formattedDeliverableTotalPrice,
            String reservationDate,
            String pickupDate,
            String paymentDate,
            List<CardReservation> items
    ) {
    }

    public record StockUpdateResponse(
            int rowIndex,
            int stockQuantity,
            String action,
            String message
    ) {
    }

    public record StockDeleteResponse(
            int rowIndex,
            boolean deleted,
            String message
    ) {
    }
}


