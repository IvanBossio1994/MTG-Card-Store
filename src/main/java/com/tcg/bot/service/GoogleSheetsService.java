package com.tcg.bot.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tcg.bot.dto.CardKingdomProduct;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.InventoryMovement;
import com.tcg.bot.model.CashRegisterEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GoogleSheetsService {

    private static final String APPLICATION_NAME = "TCG Inventory Bot";
    private static final String DEFAULT_INVENTORY_SHEET_NAME = "Inventario";
    private static final String MOVEMENTS_SHEET_NAME = "Movimientos";
    private static final String CASH_SHEET_NAME = "Caja";

    private final StoreSettingsService storeSettingsService;
    private final String configuredCredentialsPath;
    private final String configuredServiceAccountEmail;
    private volatile String serviceAccountEmail;

    public GoogleSheetsService(
            StoreSettingsService storeSettingsService,
            @Value("${google.credentials.path:./data/google-credentials.json}") String configuredCredentialsPath,
            @Value("${google.service-account-email:tcg-bot-service@tcg-inventory-bot.iam.gserviceaccount.com}") String configuredServiceAccountEmail
    ) {
        this.storeSettingsService = storeSettingsService;
        this.configuredCredentialsPath = configuredCredentialsPath;
        this.configuredServiceAccountEmail = configuredServiceAccountEmail;
    }

    public Sheets getSheetsService() throws Exception {
        GoogleCredentials credentials;

        try (InputStream credentialsStream = openCredentialsStream()) {
            credentials = GoogleCredentials
                    .fromStream(credentialsStream)
                    .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));
        }

        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        )
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public String getServiceAccountEmail() {
        if (serviceAccountEmail != null) {
            return serviceAccountEmail;
        }

        try (InputStream credentialsStream = openCredentialsStream()) {
            JsonObject credentials = JsonParser
                    .parseString(new String(credentialsStream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();

            serviceAccountEmail = credentials.has("client_email")
                    ? credentials.get("client_email").getAsString()
                    : configuredServiceAccountEmail;
            return serviceAccountEmail;
        } catch (Exception e) {
            serviceAccountEmail = configuredServiceAccountEmail;
            return serviceAccountEmail;
        }
    }

    public boolean hasCredentialsConfigured() {
        String environmentCredentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");

        if (externalCredentialsExists(environmentCredentialsPath)) {
            return true;
        }

        if (externalCredentialsExists(configuredCredentialsPath)) {
            return true;
        }

        return getClass()
                .getClassLoader()
                .getResource("credentials/google-credentials.json") != null;
    }

    public String getConfiguredCredentialsPath() {
        return configuredCredentialsPath;
    }

    public void clearServiceAccountEmailCache() {
        serviceAccountEmail = null;
    }

    private InputStream openCredentialsStream() throws Exception {
        String environmentCredentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");

        InputStream externalCredentials = openExternalCredentials(environmentCredentialsPath);
        if (externalCredentials != null) {
            return externalCredentials;
        }

        externalCredentials = openExternalCredentials(configuredCredentialsPath);
        if (externalCredentials != null) {
            return externalCredentials;
        }

        InputStream bundledCredentials = getClass()
                .getClassLoader()
                .getResourceAsStream("credentials/google-credentials.json");

        if (bundledCredentials != null) {
            return bundledCredentials;
        }

        throw new RuntimeException(
                "No se encontro google-credentials.json. Colocalo en " + configuredCredentialsPath + " o configura GOOGLE_APPLICATION_CREDENTIALS."
        );
    }

    private boolean externalCredentialsExists(String credentialsPath) {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            return false;
        }

        return Files.exists(Path.of(credentialsPath.trim()));
    }

    private InputStream openExternalCredentials(String credentialsPath) throws Exception {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            return null;
        }

        Path path = Path.of(credentialsPath.trim());

        if (!Files.exists(path)) {
            return null;
        }

        return Files.newInputStream(path);
    }

    public List<InventoryCard> getInventoryCards() throws Exception {
        Sheets sheetsService = getSheetsService();
        ensureInventorySheet(sheetsService);

        var response = sheetsService.spreadsheets().values()
                .get(storeSettingsService.getSpreadsheetId(), range("A1:L"))
                .execute();

        var values = response.getValues();
        List<InventoryCard> cards = new ArrayList<>();

        if (values == null || values.size() <= 1) {
            return cards;
        }

        SheetColumns columns = sheetColumns(values.get(0));

        for (int index = 1; index < values.size(); index++) {
            var row = values.get(index);
            InventoryCard card = new InventoryCard();
            card.setRowIndex(index + 1);
            card.setQuantity(columns.get(row, ColumnKey.QUANTITY));
            card.setName(columns.get(row, ColumnKey.NAME));
            card.setSetCode(columns.get(row, ColumnKey.SET_CODE));
            card.setSetName(columns.get(row, ColumnKey.SET_NAME));
            card.setCollectorNumber(columns.get(row, ColumnKey.COLLECTOR_NUMBER));
            card.setCondition(columns.get(row, ColumnKey.CONDITION));
            card.setPrinting(columns.get(row, ColumnKey.PRINTING));
            card.setLanguage(columns.get(row, ColumnKey.LANGUAGE));
            card.setLocalPrice(columns.get(row, ColumnKey.LOCAL_PRICE));
            card.setCkPriceUsd(columns.get(row, ColumnKey.CK_PRICE_USD));
            card.setAction(columns.get(row, ColumnKey.ACTION));
            cards.add(card);
        }

        return cards;
    }

    public void prepareInventorySheet(List<CardKingdomProduct> products) throws Exception {
        Sheets sheetsService = getSheetsService();
        ensureInventorySheet(sheetsService, products);
    }

    public InventoryCard findInventoryCard(
            String name,
            String setName,
            String sku,
            String collectorNumber,
            boolean foil
    ) throws Exception {
        var cards = getInventoryCards();

        for (var card : cards) {
            if (card.getName() == null || !card.getName().equalsIgnoreCase(name)) {
                continue;
            }

            if (setName != null
                    && card.getSetName() != null
                    && !setName.equalsIgnoreCase(card.getSetName())) {
                continue;
            }

            if (sku != null
                    && card.getSetCode() != null
                    && !card.getSetCode().isBlank()
                    && !sku.toLowerCase().startsWith(card.getSetCode().toLowerCase() + "-")) {
                continue;
            }

            if (collectorNumber != null && card.getCollectorNumber() != null) {
                String localCollector = normalizeCollector(card.getCollectorNumber());
                String searchCollector = normalizeCollector(collectorNumber);

                if (!localCollector.equals(searchCollector)) {
                    continue;
                }
            }

            if (card.isFoil() != foil) {
                continue;
            }

            int quantity;
            try {
                quantity = Integer.parseInt(card.getQuantity().trim());
            } catch (NumberFormatException e) {
                continue;
            }

            if (quantity <= 0) {
                continue;
            }

            return card;
        }

        return null;
    }

    public void updateInventoryRow(int rowIndex, InventoryCard card) throws Exception {
        Sheets sheetsService = getSheetsService();
        SheetColumns columns = sheetColumns(sheetsService);

        batchUpdate(sheetsService, automaticUpdateRanges(rowIndex, card, columns));
    }

    public void updateStockState(int rowIndex, InventoryCard card) throws Exception {
        Sheets sheetsService = getSheetsService();
        SheetColumns columns = sheetColumns(sheetsService);

        batchUpdate(sheetsService, List.of(
                valueRange(columns.cell(ColumnKey.QUANTITY, rowIndex), numericOrBlank(card.getQuantity())),
                valueRange(columns.cell(ColumnKey.ACTION, rowIndex), card.getAction() == null ? "" : card.getAction())
        ));
    }

    public void updateQuantity(int rowIndex, int change) throws Exception {
        Sheets sheetsService = getSheetsService();
        SheetColumns columns = sheetColumns(sheetsService);
        String quantityRange = range(columns.cell(ColumnKey.QUANTITY, rowIndex));

        var response = sheetsService.spreadsheets().values()
                .get(storeSettingsService.getSpreadsheetId(), quantityRange)
                .execute();

        List<List<Object>> values = response.getValues();
        int currentQuantity = 0;

        if (values != null && !values.isEmpty() && !values.get(0).isEmpty()) {
            try {
                currentQuantity = Integer.parseInt(values.get(0).get(0).toString());
            } catch (NumberFormatException e) {
                currentQuantity = 0;
            }
        }

        int updatedQuantity = Math.max(currentQuantity + change, 0);

        var body = new com.google.api.services.sheets.v4.model.ValueRange()
                .setValues(List.of(List.of(updatedQuantity)));

        sheetsService.spreadsheets().values()
                .update(storeSettingsService.getSpreadsheetId(), quantityRange, body)
                .setValueInputOption("RAW")
                .execute();
    }

    public int appendInventoryCard(InventoryCard card) throws Exception {
        Sheets sheetsService = getSheetsService();
        SheetColumns columns = sheetColumns(sheetsService);

        var body = new com.google.api.services.sheets.v4.model.ValueRange()
                .setValues(List.of(rowValues(card, columns)));

        var response = sheetsService.spreadsheets().values()
                .append(storeSettingsService.getSpreadsheetId(), range("A:L"), body)
                .setValueInputOption("RAW")
                .setInsertDataOption("INSERT_ROWS")
                .execute();

        String updatedRange = response.getUpdates() == null
                ? ""
                : response.getUpdates().getUpdatedRange();

        return rowIndexFromUpdatedRange(updatedRange);
    }

    public void appendInventoryCards(List<InventoryCard> cards) throws Exception {
        if (cards == null || cards.isEmpty()) {
            return;
        }

        Sheets sheetsService = getSheetsService();
        SheetColumns columns = sheetColumns(sheetsService);
        List<List<Object>> bodyValues = new ArrayList<>();

        for (InventoryCard card : cards) {
            bodyValues.add(rowValues(card, columns));
        }

        var body = new com.google.api.services.sheets.v4.model.ValueRange()
                .setValues(bodyValues);

        sheetsService.spreadsheets().values()
                .append(storeSettingsService.getSpreadsheetId(), range("A:L"), body)
                .setValueInputOption("RAW")
                .setInsertDataOption("INSERT_ROWS")
                .execute();
    }

    public void updateQuantities(Map<Integer, Integer> quantitiesByRow) throws Exception {
        if (quantitiesByRow == null || quantitiesByRow.isEmpty()) {
            return;
        }

        Sheets sheetsService = getSheetsService();
        SheetColumns columns = sheetColumns(sheetsService);
        List<com.google.api.services.sheets.v4.model.ValueRange> data = new ArrayList<>();

        for (var entry : quantitiesByRow.entrySet()) {
            data.add(new com.google.api.services.sheets.v4.model.ValueRange()
                    .setRange(range(columns.cell(ColumnKey.QUANTITY, entry.getKey())))
                    .setValues(List.of(List.of(entry.getValue()))));
        }

        batchUpdate(sheetsService, data);
    }

    public void updateInventoryRows(Map<Integer, InventoryCard> cardsByRow) throws Exception {
        if (cardsByRow == null || cardsByRow.isEmpty()) {
            return;
        }

        Sheets sheetsService = getSheetsService();
        SheetColumns columns = sheetColumns(sheetsService);
        List<com.google.api.services.sheets.v4.model.ValueRange> data = new ArrayList<>();

        for (var entry : cardsByRow.entrySet()) {
            data.addAll(automaticUpdateRanges(entry.getKey(), entry.getValue(), columns));
        }

        batchUpdate(sheetsService, data);
    }

    public void sortInventoryByName() throws Exception {
        Sheets sheetsService = getSheetsService();
        SheetColumns columns = sheetColumns(sheetsService);
        Integer sheetId = inventorySheetId(sheetsService);

        if (sheetId == null) {
            return;
        }

        var sortRequest = new com.google.api.services.sheets.v4.model.Request()
                .setSortRange(new com.google.api.services.sheets.v4.model.SortRangeRequest()
                        .setRange(new com.google.api.services.sheets.v4.model.GridRange()
                                .setSheetId(sheetId)
                                .setStartRowIndex(1)
                                .setStartColumnIndex(0)
                                .setEndColumnIndex(columns.width()))
                        .setSortSpecs(List.of(
                                sortSpec(columns.index(ColumnKey.NAME)),
                                sortSpec(columns.index(ColumnKey.SET_NAME)),
                                sortSpec(columns.index(ColumnKey.SET_CODE)),
                                sortSpec(columns.index(ColumnKey.COLLECTOR_NUMBER))
                        )));

        var batchRequest = new com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest()
                .setRequests(List.of(sortRequest));

        sheetsService.spreadsheets()
                .batchUpdate(storeSettingsService.getSpreadsheetId(), batchRequest)
                .execute();
    }

    public void updateLocalPrice(int rowIndex, String localPrice) throws Exception {
        Sheets sheetsService = getSheetsService();
        SheetColumns columns = sheetColumns(sheetsService);

        var body = new com.google.api.services.sheets.v4.model.ValueRange()
                .setValues(List.of(List.of(Double.parseDouble(localPrice.replace(",", ".")))));

        sheetsService.spreadsheets().values()
                .update(storeSettingsService.getSpreadsheetId(), range(columns.cell(ColumnKey.LOCAL_PRICE, rowIndex)), body)
                .setValueInputOption("RAW")
                .execute();
    }

    public List<InventoryMovement> getRecentMovements() throws Exception {
        Sheets sheetsService = getSheetsService();
        ensureMovementsSheet(sheetsService);

        var response = sheetsService.spreadsheets().values()
                .get(storeSettingsService.getSpreadsheetId(), movementRange("A2:L"))
                .execute();

        var values = response.getValues();
        List<InventoryMovement> movements = new ArrayList<>();

        if (values == null || values.isEmpty()) {
            return movements;
        }

        for (var row : values) {
            boolean legacyDateTimeFormat = isMovementType(getColumnValue(row, 1));
            boolean typedDateTimeFormat = isMovementType(getColumnValue(row, 2));

            String dateTime = getColumnValue(row, 0);
            String date = legacyDateTimeFormat ? datePart(dateTime) : getColumnValue(row, 0);
            String time = legacyDateTimeFormat ? timePart(dateTime) : getColumnValue(row, 1);

            String type;
            String quantity;
            String name;
            String setName;
            String setCode;
            String collectorNumber;
            String printing;
            String previousStock;
            String newStock;
            String source;

            if (legacyDateTimeFormat) {
                type = getColumnValue(row, 1);
                quantity = signedQuantity(type, getColumnValue(row, 2));
                name = getColumnValue(row, 3);
                setName = getColumnValue(row, 4);
                setCode = getColumnValue(row, 5);
                collectorNumber = getColumnValue(row, 6);
                printing = getColumnValue(row, 7);
                previousStock = getColumnValue(row, 8);
                newStock = getColumnValue(row, 9);
                source = getColumnValue(row, 10);
            } else if (typedDateTimeFormat) {
                type = getColumnValue(row, 2);
                quantity = signedQuantity(type, getColumnValue(row, 3));
                name = getColumnValue(row, 4);
                setName = getColumnValue(row, 5);
                setCode = getColumnValue(row, 6);
                collectorNumber = getColumnValue(row, 7);
                printing = getColumnValue(row, 8);
                previousStock = getColumnValue(row, 9);
                newStock = getColumnValue(row, 10);
                source = getColumnValue(row, 11);
            } else {
                quantity = getColumnValue(row, 2);
                type = quantity.startsWith("-") ? "SALIDA" : "ENTRADA";
                name = getColumnValue(row, 3);
                setName = getColumnValue(row, 4);
                setCode = getColumnValue(row, 5);
                collectorNumber = getColumnValue(row, 6);
                printing = getColumnValue(row, 7);
                previousStock = getColumnValue(row, 8);
                newStock = getColumnValue(row, 9);
                source = getColumnValue(row, 10);
            }

            movements.add(new InventoryMovement(
                    dateTime,
                    date,
                    time,
                    type,
                    quantity,
                    name,
                    setName,
                    setCode,
                    collectorNumber,
                    printing,
                    previousStock,
                    newStock,
                    source
            ));
        }

        Collections.reverse(movements);
        return movements;
    }

    public void appendMovement(InventoryMovement movement) throws Exception {
        appendMovements(List.of(movement));
    }

    public void appendMovements(List<InventoryMovement> movements) throws Exception {
        if (movements == null || movements.isEmpty()) {
            return;
        }

        Sheets sheetsService = getSheetsService();
        ensureMovementsSheet(sheetsService);

        List<List<Object>> values = new ArrayList<>();

        for (InventoryMovement movement : movements) {
            values.add(List.of(
                    movement.getDate(),
                    movement.getTime(),
                    movement.getQuantity(),
                    movement.getName(),
                    movement.getSetName(),
                    movement.getSetCode(),
                    movement.getCollectorNumber(),
                    movement.getPrinting(),
                    movement.getPreviousStock(),
                    movement.getNewStock(),
                    movement.getSource()
            ));
        }

        var body = new com.google.api.services.sheets.v4.model.ValueRange()
                .setValues(values);

        sheetsService.spreadsheets().values()
                .append(storeSettingsService.getSpreadsheetId(), movementRange("A:K"), body)
                .setValueInputOption("RAW")
                .setInsertDataOption("INSERT_ROWS")
                .execute();
    }

    public List<CashRegisterEntry> getCashRegisterEntries() throws Exception {
        Sheets sheetsService = getSheetsService();
        ensureCashSheet(sheetsService);

        var response = sheetsService.spreadsheets().values()
                .get(storeSettingsService.getSpreadsheetId(), cashRange("A2:L"))
                .execute();

        var values = response.getValues();
        List<CashRegisterEntry> entries = new ArrayList<>();

        if (values == null || values.isEmpty()) {
            return entries;
        }

        for (var row : values) {
            CashRegisterEntry entry = cashEntryFromRow(row);

            if ("VENTA".equalsIgnoreCase(entry.getType())) {
                entries.add(entry);
            }
        }

        Collections.reverse(entries);
        return entries;
    }

    public void appendCashSale(String date, String time, InventoryCard card, int quantity) throws Exception {
        Sheets sheetsService = getSheetsService();
        ensureCashSheet(sheetsService);

        double localPrice = parseCashNumber(card.getLocalPrice());
        double total = localPrice * quantity;
        double dayTotal = totalCashSalesForDate(sheetsService, date) + total;

        appendCashRow(sheetsService, List.of(
                safe(date),
                safe(time),
                safe(card.getName()),
                safe(card.getSetName()),
                safe(card.getSetCode()),
                safe(card.getCollectorNumber()),
                safe(card.getPrinting()),
                formatCashNumber(localPrice),
                String.valueOf(quantity),
                formatCashNumber(total),
                formatCashNumber(dayTotal)
        ));
    }

    private CashRegisterEntry cashEntryFromRow(List<Object> row) {
        CashRegisterEntry entry = new CashRegisterEntry();
        String thirdColumn = getColumnValue(row, 2);
        boolean legacyFormat = isCashEntryType(thirdColumn);

        entry.setDate(getColumnValue(row, 0));
        entry.setTime(getColumnValue(row, 1));
        entry.setType(legacyFormat ? thirdColumn : "VENTA");

        if (legacyFormat) {
            entry.setName(getColumnValue(row, 3));
            entry.setSetName(getColumnValue(row, 4));
            entry.setSetCode(getColumnValue(row, 5));
            entry.setCollectorNumber(getColumnValue(row, 6));
            entry.setPrinting(getColumnValue(row, 7));
            entry.setLocalPrice(getColumnValue(row, 8));
            entry.setQuantity(getColumnValue(row, 9));
            entry.setTotal(getColumnValue(row, 10));
            entry.setDayTotal("");
            entry.setStatus(getColumnValue(row, 11));
            return entry;
        }

        entry.setName(getColumnValue(row, 2));
        entry.setSetName(getColumnValue(row, 3));
        entry.setSetCode(getColumnValue(row, 4));
        entry.setCollectorNumber(getColumnValue(row, 5));
        entry.setPrinting(getColumnValue(row, 6));
        entry.setLocalPrice(getColumnValue(row, 7));
        entry.setQuantity(getColumnValue(row, 8));
        entry.setTotal(getColumnValue(row, 9));
        entry.setDayTotal(getColumnValue(row, 10));
        entry.setStatus("");
        return entry;
    }

    private boolean isCashEntryType(String value) {
        return "VENTA".equalsIgnoreCase(value)
                || "APERTURA".equalsIgnoreCase(value)
                || "CIERRE".equalsIgnoreCase(value);
    }

    private boolean isMovementType(String value) {
        return "ENTRADA".equalsIgnoreCase(value) || "SALIDA".equalsIgnoreCase(value);
    }

    private String signedQuantity(String type, String quantity) {
        if (quantity == null || quantity.isBlank()) {
            return "";
        }

        String normalizedQuantity = quantity.trim();
        if ("SALIDA".equalsIgnoreCase(type) && !normalizedQuantity.startsWith("-")) {
            return "-" + normalizedQuantity;
        }

        return normalizedQuantity;
    }

    private String datePart(String dateTime) {
        if (dateTime == null || dateTime.length() < 10) {
            return "";
        }

        return dateTime.substring(0, 10);
    }

    private String timePart(String dateTime) {
        if (dateTime == null || dateTime.length() < 19) {
            return "";
        }

        return dateTime.substring(11, 19);
    }

    private void batchUpdate(
            Sheets sheetsService,
            List<com.google.api.services.sheets.v4.model.ValueRange> data
    ) throws Exception {
        if (data == null || data.isEmpty()) {
            return;
        }

        var body = new com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest()
                .setValueInputOption("RAW")
                .setData(data);

        sheetsService.spreadsheets().values()
                .batchUpdate(storeSettingsService.getSpreadsheetId(), body)
                .execute();
    }

    private com.google.api.services.sheets.v4.model.SortSpec sortSpec(int columnIndex) {
        return new com.google.api.services.sheets.v4.model.SortSpec()
                .setDimensionIndex(columnIndex)
                .setSortOrder("ASCENDING");
    }

    private Integer inventorySheetId(Sheets sheetsService) throws Exception {
        ensureInventorySheet(sheetsService);

        var spreadsheet = sheetsService.spreadsheets()
                .get(storeSettingsService.getSpreadsheetId())
                .setFields("sheets.properties(sheetId,title)")
                .execute();

        String inventorySheetName = storeSettingsService.getInventorySheetName();

        if (spreadsheet.getSheets() == null) {
            return null;
        }

        return spreadsheet.getSheets()
                .stream()
                .filter(sheet -> inventorySheetName.equals(sheet.getProperties().getTitle()))
                .map(sheet -> sheet.getProperties().getSheetId())
                .findFirst()
                .orElse(null);
    }

    private List<com.google.api.services.sheets.v4.model.ValueRange> automaticUpdateRanges(
            int rowIndex,
            InventoryCard card,
            SheetColumns columns
    ) {
        return List.of(
                valueRange(columns.cell(ColumnKey.LOCAL_PRICE, rowIndex), numericOrBlank(card.getLocalPrice())),
                valueRange(columns.cell(ColumnKey.CK_PRICE_USD, rowIndex), numericOrBlank(card.getCkPriceUsd())),
                valueRange(columns.cell(ColumnKey.ACTION, rowIndex), card.getAction() == null ? "" : card.getAction())
        );
    }

    private com.google.api.services.sheets.v4.model.ValueRange valueRange(String cell, Object value) {
        return new com.google.api.services.sheets.v4.model.ValueRange()
                .setRange(range(cell))
                .setValues(List.of(List.of(value)));
    }

    private List<Object> rowValues(InventoryCard card, SheetColumns columns) {
        List<Object> values = new ArrayList<>(Collections.nCopies(columns.width(), ""));
        set(values, columns.index(ColumnKey.QUANTITY), card.getQuantity());
        set(values, columns.index(ColumnKey.NAME), card.getName());
        set(values, columns.index(ColumnKey.SET_CODE), card.getSetCode());
        set(values, columns.index(ColumnKey.SET_NAME), card.getSetName());
        set(values, columns.index(ColumnKey.COLLECTOR_NUMBER), card.getCollectorNumber());
        set(values, columns.index(ColumnKey.CONDITION), card.getCondition());
        set(values, columns.index(ColumnKey.PRINTING), card.getPrinting());
        set(values, columns.index(ColumnKey.LANGUAGE), card.getLanguage());
        set(values, columns.index(ColumnKey.LOCAL_PRICE), card.getLocalPrice());
        set(values, columns.index(ColumnKey.CK_PRICE_USD), card.getCkPriceUsd());
        set(values, columns.index(ColumnKey.ACTION), card.getAction());
        return values;
    }

    private void set(List<Object> values, int index, String value) {
        if (index >= 0 && index < values.size()) {
            values.set(index, value == null ? "" : value);
        }
    }

    private SheetColumns sheetColumns(Sheets sheetsService) throws Exception {
        ensureInventorySheet(sheetsService);

        var response = sheetsService.spreadsheets().values()
                .get(storeSettingsService.getSpreadsheetId(), range("A1:L1"))
                .execute();

        var values = response.getValues();

        if (values == null || values.isEmpty()) {
            return SheetColumns.defaults();
        }

        return sheetColumns(values.get(0));
    }

    private SheetColumns sheetColumns(List<Object> header) {
        return SheetColumns.fromHeader(header);
    }

    private String range(String cells) {
        return sheetRange(storeSettingsService.getInventorySheetName(), cells);
    }

    private String sheetRange(String sheetName, String cells) {
        String escapedSheetName = sheetName.replace("'", "''");
        return "'" + escapedSheetName + "'!" + cells;
    }

    private String movementRange(String cells) {
        return "'" + MOVEMENTS_SHEET_NAME + "'!" + cells;
    }

    private String cashRange(String cells) {
        return "'" + CASH_SHEET_NAME + "'!" + cells;
    }

    private void ensureInventorySheet(Sheets sheetsService) throws Exception {
        ensureInventorySheet(sheetsService, List.of());
    }

    private void ensureInventorySheet(Sheets sheetsService, List<CardKingdomProduct> products) throws Exception {
        var spreadsheet = sheetsService.spreadsheets()
                .get(storeSettingsService.getSpreadsheetId())
                .setFields("sheets.properties.title")
                .execute();

        String inventorySheetName = storeSettingsService.getInventorySheetName();
        List<String> sheetTitles = spreadsheet.getSheets() == null
                ? List.of()
                : spreadsheet.getSheets().stream()
                .map(sheet -> sheet.getProperties().getTitle())
                .toList();
        String requestedInventorySheetName = inventorySheetName;
        boolean exists = spreadsheet.getSheets() != null
                && spreadsheet.getSheets().stream()
                .anyMatch(sheet -> requestedInventorySheetName.equals(sheet.getProperties().getTitle()));

        if (!exists && products != null && !products.isEmpty()
                && migrateExistingInventoryFromAnySheetIfNeeded(sheetsService, sheetTitles, products, inventorySheetName)) {
            return;
        }

        if (!exists && sheetTitles.size() == 1) {
            inventorySheetName = sheetTitles.get(0);
            storeSettingsService.useInventorySheetName(inventorySheetName);
            exists = true;
        }

        if (!exists) {
            var addSheetRequest = new com.google.api.services.sheets.v4.model.Request()
                    .setAddSheet(new com.google.api.services.sheets.v4.model.AddSheetRequest()
                            .setProperties(new com.google.api.services.sheets.v4.model.SheetProperties()
                                    .setTitle(inventorySheetName)));

            var batchRequest = new com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest()
                    .setRequests(List.of(addSheetRequest));

            sheetsService.spreadsheets()
                    .batchUpdate(storeSettingsService.getSpreadsheetId(), batchRequest)
                    .execute();
        }

        var headerResponse = sheetsService.spreadsheets().values()
                .get(storeSettingsService.getSpreadsheetId(), range("A1:L1"))
                .execute();

        var values = headerResponse.getValues();
        List<Object> header = values == null || values.isEmpty()
                ? List.of()
                : values.get(0);

        if (migrateExistingInventorySheetIfNeeded(sheetsService, header, products)) {
            return;
        }

        migrateLegacyComputedPriceColumn(sheetsService, header);

        List<String> updatedHeader = completedInventoryHeader(header);
        if (sameHeader(header, updatedHeader)) {
            return;
        }

        String lastColumn = SheetColumns.columnLetter(updatedHeader.size() - 1);
        var headerBody = new com.google.api.services.sheets.v4.model.ValueRange()
                .setValues(List.of(new ArrayList<>(updatedHeader)));

        sheetsService.spreadsheets().values()
                .update(storeSettingsService.getSpreadsheetId(), range("A1:" + lastColumn + "1"), headerBody)
                .setValueInputOption("RAW")
                .execute();

        if (header.size() > updatedHeader.size()) {
            String firstRemovedColumn = SheetColumns.columnLetter(updatedHeader.size());
            String lastExistingColumn = SheetColumns.columnLetter(header.size() - 1);
            sheetsService.spreadsheets().values()
                    .clear(
                            storeSettingsService.getSpreadsheetId(),
                            range(firstRemovedColumn + ":" + lastExistingColumn),
                            new com.google.api.services.sheets.v4.model.ClearValuesRequest()
                    )
                    .execute();
        }
    }

    private boolean migrateExistingInventorySheetIfNeeded(
            Sheets sheetsService,
            List<Object> header,
            List<CardKingdomProduct> products
    ) throws Exception {
        if (isAppManagedHeader(header) || products == null || products.isEmpty()) {
            return false;
        }

        var response = sheetsService.spreadsheets().values()
                .get(storeSettingsService.getSpreadsheetId(), range("A1:Z"))
                .execute();

        var values = response.getValues();
        if (values == null || values.size() <= 1) {
            return false;
        }

        Map<String, CardKingdomProduct> productsByName = uniqueProductsByName(products);
        ExistingInventoryMapping mapping = detectExistingInventoryMapping(values, productsByName);

        if (mapping == null || mapping.matches() < 2) {
            return false;
        }

        String migratedSheetName = uniqueSheetName(sheetsService, DEFAULT_INVENTORY_SHEET_NAME);
        writeMigratedInventorySheet(sheetsService, migratedSheetName, values, mapping, productsByName);
        storeSettingsService.useInventorySheetName(migratedSheetName);
        return true;
    }

    private boolean migrateExistingInventoryFromAnySheetIfNeeded(
            Sheets sheetsService,
            List<String> sheetTitles,
            List<CardKingdomProduct> products,
            String targetSheetName
    ) throws Exception {
        if (sheetTitles == null || sheetTitles.isEmpty() || products == null || products.isEmpty()) {
            return false;
        }

        Map<String, CardKingdomProduct> productsByName = uniqueProductsByName(products);
        ExistingInventoryCandidate bestCandidate = null;

        for (String sheetTitle : sheetTitles) {
            if (isSystemSheet(sheetTitle)) {
                continue;
            }

            var response = sheetsService.spreadsheets().values()
                    .get(storeSettingsService.getSpreadsheetId(), sheetRange(sheetTitle, "A1:Z"))
                    .execute();

            var values = response.getValues();
            if (values == null || values.size() <= 1) {
                continue;
            }

            List<Object> header = values.get(0);
            if (isAppManagedHeader(header)) {
                storeSettingsService.useInventorySheetName(sheetTitle);
                return true;
            }

            ExistingInventoryMapping mapping = detectExistingInventoryMapping(values, productsByName);
            if (mapping == null || mapping.matches() < 2) {
                continue;
            }

            if (bestCandidate == null || mapping.matches() > bestCandidate.mapping().matches()) {
                bestCandidate = new ExistingInventoryCandidate(sheetTitle, values, mapping);
            }
        }

        if (bestCandidate == null) {
            return false;
        }

        String migratedSheetName = uniqueSheetName(sheetsService, targetSheetName);
        writeMigratedInventorySheet(
                sheetsService,
                migratedSheetName,
                bestCandidate.values(),
                bestCandidate.mapping(),
                productsByName
        );
        storeSettingsService.useInventorySheetName(migratedSheetName);
        return true;
    }

    private void writeMigratedInventorySheet(
            Sheets sheetsService,
            String migratedSheetName,
            List<List<Object>> values,
            ExistingInventoryMapping mapping,
            Map<String, CardKingdomProduct> productsByName
    ) throws Exception {
        createSheet(sheetsService, migratedSheetName);

        List<List<Object>> migratedRows = new ArrayList<>();
        migratedRows.add(new ArrayList<>(inventoryHeader()));

        for (int index = 1; index < values.size(); index++) {
            List<Object> row = values.get(index);
            String rawName = cellValue(row, mapping.nameColumn());
            CardKingdomProduct product = productsByName.get(normalizeCardName(rawName));

            if (product == null) {
                continue;
            }

            migratedRows.add(migratedRow(row, mapping, product));
        }

        var body = new com.google.api.services.sheets.v4.model.ValueRange()
                .setValues(migratedRows);

        String lastColumn = SheetColumns.columnLetter(inventoryHeader().size() - 1);
        sheetsService.spreadsheets().values()
                .update(storeSettingsService.getSpreadsheetId(), sheetRange(migratedSheetName, "A1:" + lastColumn), body)
                .setValueInputOption("RAW")
                .execute();
    }

    private void createSheet(Sheets sheetsService, String sheetName) throws Exception {
        var addSheetRequest = new com.google.api.services.sheets.v4.model.Request()
                .setAddSheet(new com.google.api.services.sheets.v4.model.AddSheetRequest()
                        .setProperties(new com.google.api.services.sheets.v4.model.SheetProperties()
                                .setTitle(sheetName)));

        var batchRequest = new com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest()
                .setRequests(List.of(addSheetRequest));

        sheetsService.spreadsheets()
                .batchUpdate(storeSettingsService.getSpreadsheetId(), batchRequest)
                .execute();
    }

    private boolean isSystemSheet(String sheetTitle) {
        return MOVEMENTS_SHEET_NAME.equals(sheetTitle)
                || CASH_SHEET_NAME.equals(sheetTitle);
    }

    private boolean isAppManagedHeader(List<Object> header) {
        if (header == null || header.isEmpty()) {
            return false;
        }

        SheetColumns columns = SheetColumns.fromHeader(header);
        return columns.has(ColumnKey.NAME)
                && columns.has(ColumnKey.QUANTITY)
                && columns.has(ColumnKey.LOCAL_PRICE)
                && columns.has(ColumnKey.CK_PRICE_USD)
                && columns.has(ColumnKey.ACTION);
    }

    private ExistingInventoryMapping detectExistingInventoryMapping(
            List<List<Object>> values,
            Map<String, CardKingdomProduct> productsByName
    ) {
        int width = values.stream().mapToInt(List::size).max().orElse(0);
        if (width == 0) {
            return null;
        }

        List<Object> header = values.get(0);
        int nameColumn = -1;
        int matches = 0;

        for (int column = 0; column < width; column++) {
            int columnMatches = 0;
            for (int rowIndex = 1; rowIndex < values.size(); rowIndex++) {
                String value = cellValue(values.get(rowIndex), column);
                if (productsByName.containsKey(normalizeCardName(value))) {
                    columnMatches++;
                }
            }

            if (columnMatches > matches) {
                matches = columnMatches;
                nameColumn = column;
            }
        }

        if (nameColumn < 0) {
            return null;
        }

        return new ExistingInventoryMapping(
                nameColumn,
                headerColumn(header, ColumnKey.QUANTITY, width, nameColumn, values),
                headerColumn(header, ColumnKey.SET_CODE, width, nameColumn, values),
                headerColumn(header, ColumnKey.SET_NAME, width, nameColumn, values),
                headerColumn(header, ColumnKey.COLLECTOR_NUMBER, width, nameColumn, values),
                headerColumn(header, ColumnKey.CONDITION, width, nameColumn, values),
                headerColumn(header, ColumnKey.PRINTING, width, nameColumn, values),
                headerColumn(header, ColumnKey.LANGUAGE, width, nameColumn, values),
                matches
        );
    }

    private int headerColumn(
            List<Object> header,
            ColumnKey key,
            int width,
            int nameColumn,
            List<List<Object>> values
    ) {
        Map<String, Integer> normalizedHeader = new HashMap<>();
        for (int column = 0; column < header.size(); column++) {
            normalizedHeader.put(SheetColumns.normalizeHeader(header.get(column).toString()), column);
        }

        for (String alias : SheetColumns.aliases(key)) {
            Integer column = normalizedHeader.get(SheetColumns.normalizeHeader(alias));
            if (column != null) {
                return column;
            }
        }

        if (key != ColumnKey.QUANTITY) {
            return -1;
        }

        int bestColumn = -1;
        int bestNumericCount = 0;
        for (int column = 0; column < width; column++) {
            if (column == nameColumn) {
                continue;
            }

            int numericCount = 0;
            for (int rowIndex = 1; rowIndex < values.size(); rowIndex++) {
                if (parseInteger(cellValue(values.get(rowIndex), column)) >= 0) {
                    numericCount++;
                }
            }

            if (numericCount > bestNumericCount) {
                bestNumericCount = numericCount;
                bestColumn = column;
            }
        }

        return bestColumn;
    }

    private List<Object> migratedRow(
            List<Object> sourceRow,
            ExistingInventoryMapping mapping,
            CardKingdomProduct product
    ) {
        String sku = product.getSku() == null ? "" : product.getSku();
        String setCode = columnOrDefault(sourceRow, mapping.setCodeColumn(), sku.contains("-") ? sku.substring(0, sku.indexOf("-")) : "");
        String setName = columnOrDefault(sourceRow, mapping.setNameColumn(), product.getEdition());
        String collectorNumber = columnOrDefault(sourceRow, mapping.collectorNumberColumn(), collectorFromSku(sku));
        String printing = columnOrDefault(sourceRow, mapping.printingColumn(), "true".equalsIgnoreCase(product.getFoil()) ? "foil" : "nonfoil");
        String quantity = columnOrDefault(sourceRow, mapping.quantityColumn(), "1");

        return List.of(
                String.valueOf(Math.max(parseInteger(quantity), 0)),
                product.getName() == null ? cellValue(sourceRow, mapping.nameColumn()) : product.getName(),
                setCode,
                setName == null ? "" : setName,
                collectorNumber,
                columnOrDefault(sourceRow, mapping.conditionColumn(), "NM"),
                printing,
                columnOrDefault(sourceRow, mapping.languageColumn(), ""),
                "",
                "",
                ""
        );
    }

    private Map<String, CardKingdomProduct> uniqueProductsByName(List<CardKingdomProduct> products) {
        Map<String, CardKingdomProduct> unique = new HashMap<>();

        for (CardKingdomProduct product : products) {
            String key = normalizeCardName(product.getName());
            if (key.isBlank()) {
                continue;
            }

            unique.putIfAbsent(key, product);
        }

        return unique;
    }

    private String uniqueSheetName(Sheets sheetsService, String baseName) throws Exception {
        var spreadsheet = sheetsService.spreadsheets()
                .get(storeSettingsService.getSpreadsheetId())
                .setFields("sheets.properties.title")
                .execute();

        Set<String> titles = new LinkedHashSet<>();
        if (spreadsheet.getSheets() != null) {
            spreadsheet.getSheets().forEach(sheet -> titles.add(sheet.getProperties().getTitle()));
        }

        if (!titles.contains(baseName)) {
            return baseName;
        }

        int suffix = 2;
        while (titles.contains(baseName + " " + suffix)) {
            suffix++;
        }

        return baseName + " " + suffix;
    }

    private String columnOrDefault(List<Object> row, int column, String fallback) {
        String value = cellValue(row, column);
        return value.isBlank() ? (fallback == null ? "" : fallback) : value;
    }

    private String cellValue(List<Object> row, int column) {
        if (column < 0 || column >= row.size() || row.get(column) == null) {
            return "";
        }

        return row.get(column).toString().trim();
    }

    private String normalizeCardName(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String collectorFromSku(String sku) {
        if (sku == null || !sku.contains("-")) {
            return "";
        }

        return sku.substring(sku.indexOf("-") + 1);
    }

    private int parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }

        try {
            return Integer.parseInt(value.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private record ExistingInventoryMapping(
            int nameColumn,
            int quantityColumn,
            int setCodeColumn,
            int setNameColumn,
            int collectorNumberColumn,
            int conditionColumn,
            int printingColumn,
            int languageColumn,
            int matches
    ) {
    }

    private record ExistingInventoryCandidate(
            String sheetTitle,
            List<List<Object>> values,
            ExistingInventoryMapping mapping
    ) {
    }

    private List<String> completedInventoryHeader(List<Object> existingHeader) {
        List<String> header = new ArrayList<>();

        for (Object value : existingHeader) {
            String headerValue = value == null ? "" : value.toString();
            if (!isLegacyComputedPriceHeader(headerValue)) {
                header.add(headerValue);
            }
        }

        if (header.stream().allMatch(String::isBlank)) {
            return inventoryHeader();
        }

        for (ColumnKey key : ColumnKey.values()) {
            if (containsColumn(header, key)) {
                continue;
            }

            int blankIndex = firstBlankIndex(header);
            if (blankIndex >= 0) {
                header.set(blankIndex, headerName(key));
            } else {
                header.add(headerName(key));
            }
        }

        return header;
    }

    private void migrateLegacyComputedPriceColumn(Sheets sheetsService, List<Object> header) throws Exception {
        int legacyIndex = headerIndex(header, this::isLegacyComputedPriceHeader);
        if (legacyIndex < 0) {
            return;
        }

        int actionIndex = headerIndex(header, value -> SheetColumns.aliases(ColumnKey.ACTION).stream()
                .anyMatch(alias -> SheetColumns.normalizeHeader(alias).equals(SheetColumns.normalizeHeader(value))));

        if (actionIndex > legacyIndex) {
            String sourceColumn = SheetColumns.columnLetter(actionIndex);
            String targetColumn = SheetColumns.columnLetter(legacyIndex);

            var response = sheetsService.spreadsheets().values()
                    .get(storeSettingsService.getSpreadsheetId(), range(sourceColumn + "2:" + sourceColumn))
                    .execute();

            var rows = response.getValues();
            if (rows != null && !rows.isEmpty()) {
                var body = new com.google.api.services.sheets.v4.model.ValueRange()
                        .setValues(rows);

                sheetsService.spreadsheets().values()
                        .update(storeSettingsService.getSpreadsheetId(), range(targetColumn + "2:" + targetColumn), body)
                        .setValueInputOption("RAW")
                        .execute();
            }
        }
    }

    private int headerIndex(List<Object> header, java.util.function.Predicate<String> predicate) {
        for (int index = 0; index < header.size(); index++) {
            String value = header.get(index) == null ? "" : header.get(index).toString();
            if (predicate.test(value)) {
                return index;
            }
        }

        return -1;
    }

    private boolean isLegacyComputedPriceHeader(String value) {
        String normalized = SheetColumns.normalizeHeader(value);
        return normalized.equals(legacyHeader(112, 114, 101, 99, 105, 111, 32, 115, 117, 103, 101, 114, 105, 100, 111))
                || normalized.equals(legacyHeader(112, 114, 101, 99, 105, 111, 32, 115, 117, 103, 101, 114, 105, 100, 111, 32, 97, 114, 115));
    }

    private String legacyHeader(int... chars) {
        StringBuilder value = new StringBuilder();
        for (int character : chars) {
            value.append((char) character);
        }

        return value.toString();
    }

    private boolean sameHeader(List<Object> existingHeader, List<String> updatedHeader) {
        if (existingHeader.size() != updatedHeader.size()) {
            return false;
        }

        for (int index = 0; index < existingHeader.size(); index++) {
            String current = existingHeader.get(index) == null ? "" : existingHeader.get(index).toString();
            if (!current.equals(updatedHeader.get(index))) {
                return false;
            }
        }

        return true;
    }

    private boolean containsColumn(List<String> header, ColumnKey key) {
        Map<String, Integer> normalizedHeader = new HashMap<>();

        for (int index = 0; index < header.size(); index++) {
            normalizedHeader.put(SheetColumns.normalizeHeader(header.get(index)), index);
        }

        for (String alias : SheetColumns.aliases(key)) {
            if (normalizedHeader.containsKey(SheetColumns.normalizeHeader(alias))) {
                return true;
            }
        }

        return false;
    }

    private int firstBlankIndex(List<String> header) {
        for (int index = 0; index < header.size(); index++) {
            if (header.get(index).isBlank()) {
                return index;
            }
        }

        return -1;
    }

    private List<String> inventoryHeader() {
        List<String> header = new ArrayList<>();

        for (ColumnKey key : ColumnKey.values()) {
            header.add(headerName(key));
        }

        return header;
    }

    private String headerName(ColumnKey key) {
        return switch (key) {
            case QUANTITY -> "Cantidad";
            case NAME -> "Nombre";
            case SET_CODE -> "Codigo de set";
            case SET_NAME -> "Nombre del set";
            case COLLECTOR_NUMBER -> "Numero de carta";
            case CONDITION -> "Condicion";
            case PRINTING -> "Printing (foil/no foil)";
            case LANGUAGE -> "Idioma";
            case LOCAL_PRICE -> "Precio Local";
            case CK_PRICE_USD -> "Precio CK USD";
            case ACTION -> "Accion";
        };
    }

    private void ensureMovementsSheet(Sheets sheetsService) throws Exception {
        var spreadsheet = sheetsService.spreadsheets()
                .get(storeSettingsService.getSpreadsheetId())
                .setFields("sheets.properties.title")
                .execute();

        boolean exists = spreadsheet.getSheets() != null
                && spreadsheet.getSheets().stream()
                .anyMatch(sheet -> MOVEMENTS_SHEET_NAME.equals(sheet.getProperties().getTitle()));

        if (!exists) {
            var addSheetRequest = new com.google.api.services.sheets.v4.model.Request()
                    .setAddSheet(new com.google.api.services.sheets.v4.model.AddSheetRequest()
                            .setProperties(new com.google.api.services.sheets.v4.model.SheetProperties()
                                    .setTitle(MOVEMENTS_SHEET_NAME)));

            var batchRequest = new com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest()
                    .setRequests(List.of(addSheetRequest));

            sheetsService.spreadsheets()
                    .batchUpdate(storeSettingsService.getSpreadsheetId(), batchRequest)
                    .execute();
        }

        var headerResponse = sheetsService.spreadsheets().values()
                .get(storeSettingsService.getSpreadsheetId(), movementRange("A1:L1"))
                .execute();

        if (headerResponse.getValues() != null
                && !headerResponse.getValues().isEmpty()) {
            var header = headerResponse.getValues().get(0);
            boolean alreadyUpdated = header.size() > 2
                    && "Hora".equalsIgnoreCase(header.get(1).toString())
                    && !"Tipo".equalsIgnoreCase(header.get(2).toString())
                    && (header.size() < 12 || header.get(11).toString().isBlank());

            if (alreadyUpdated) {
                return;
            }
        }

        var headerBody = new com.google.api.services.sheets.v4.model.ValueRange()
                .setValues(List.of(List.of(
                        "Fecha",
                        "Hora",
                        "Cantidad",
                        "Nombre",
                        "Nombre del set",
                        "Código de set",
                        "Número de carta",
                        "Printing",
                        "Stock anterior",
                        "Stock nuevo",
                        "Acción"
                )));

        sheetsService.spreadsheets().values()
                .update(storeSettingsService.getSpreadsheetId(), movementRange("A1:K1"), headerBody)
                .setValueInputOption("RAW")
                .execute();

        sheetsService.spreadsheets().values()
                .clear(
                        storeSettingsService.getSpreadsheetId(),
                        movementRange("L1:L1"),
                        new com.google.api.services.sheets.v4.model.ClearValuesRequest()
                )
                .execute();
    }

    private void ensureCashSheet(Sheets sheetsService) throws Exception {
        var spreadsheet = sheetsService.spreadsheets()
                .get(storeSettingsService.getSpreadsheetId())
                .setFields("sheets.properties.title")
                .execute();

        boolean exists = spreadsheet.getSheets() != null
                && spreadsheet.getSheets().stream()
                .anyMatch(sheet -> CASH_SHEET_NAME.equals(sheet.getProperties().getTitle()));

        if (!exists) {
            var addSheetRequest = new com.google.api.services.sheets.v4.model.Request()
                    .setAddSheet(new com.google.api.services.sheets.v4.model.AddSheetRequest()
                            .setProperties(new com.google.api.services.sheets.v4.model.SheetProperties()
                                    .setTitle(CASH_SHEET_NAME)));

            var batchRequest = new com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest()
                    .setRequests(List.of(addSheetRequest));

            sheetsService.spreadsheets()
                    .batchUpdate(storeSettingsService.getSpreadsheetId(), batchRequest)
                    .execute();
        }

        var headerResponse = sheetsService.spreadsheets().values()
                .get(storeSettingsService.getSpreadsheetId(), cashRange("A1:K1"))
                .execute();

        List<String> cashHeader = List.of(
                "Fecha",
                "Hora",
                "Nombre",
                "Nombre del set",
                "Codigo de set",
                "Numero de carta",
                "Printing",
                "Precio Local",
                "Cantidad",
                "Total",
                "Total Dia"
        );

        if (headerResponse.getValues() != null
                && !headerResponse.getValues().isEmpty()
                && sameHeader(headerResponse.getValues().get(0), cashHeader)) {
            return;
        }

        var headerBody = new com.google.api.services.sheets.v4.model.ValueRange()
                .setValues(List.of(new ArrayList<>(cashHeader)));

        sheetsService.spreadsheets().values()
                .update(storeSettingsService.getSpreadsheetId(), cashRange("A1:K1"), headerBody)
                .setValueInputOption("RAW")
                .execute();

        sheetsService.spreadsheets().values()
                .clear(
                        storeSettingsService.getSpreadsheetId(),
                        cashRange("L1:L1"),
                        new com.google.api.services.sheets.v4.model.ClearValuesRequest()
                )
                .execute();
    }

    private void appendCashRow(Sheets sheetsService, List<Object> values) throws Exception {
        var body = new com.google.api.services.sheets.v4.model.ValueRange()
                .setValues(List.of(values));

        sheetsService.spreadsheets().values()
                .append(storeSettingsService.getSpreadsheetId(), cashRange("A:K"), body)
                .setValueInputOption("RAW")
                .setInsertDataOption("INSERT_ROWS")
                .execute();
    }

    private double totalCashSalesForDate(Sheets sheetsService, String date) throws Exception {
        var response = sheetsService.spreadsheets().values()
                .get(storeSettingsService.getSpreadsheetId(), cashRange("A2:L"))
                .execute();

        var values = response.getValues();
        if (values == null || values.isEmpty()) {
            return 0;
        }

        double total = 0;

        for (var row : values) {
            CashRegisterEntry entry = cashEntryFromRow(row);

            if (safe(date).equals(entry.getDate())
                    && "VENTA".equalsIgnoreCase(entry.getType())) {
                total += parseCashNumber(entry.getTotal());
            }
        }

        return total;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private double parseCashNumber(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            return Double.parseDouble(value.replace("$", "").replace(",", ".").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatCashNumber(double value) {
        return String.format(java.util.Locale.US, "%.0f", value);
    }

    private int rowIndexFromUpdatedRange(String updatedRange) {
        if (updatedRange == null || updatedRange.isBlank()) {
            return 0;
        }

        String firstCell = updatedRange.contains("!")
                ? updatedRange.substring(updatedRange.indexOf("!") + 1)
                : updatedRange;

        if (firstCell.contains(":")) {
            firstCell = firstCell.substring(0, firstCell.indexOf(":"));
        }

        String rowNumber = firstCell.replaceAll("[^0-9]", "");

        if (rowNumber.isBlank()) {
            return 0;
        }

        return Integer.parseInt(rowNumber);
    }

    private Object numericOrBlank(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return Double.parseDouble(value.replace(",", "."));
    }

    private String getColumnValue(List<Object> row, int index) {
        if (index >= row.size()) {
            return "";
        }

        return row.get(index).toString();
    }

    private String normalizeCollector(String value) {
        return value
                .replaceAll("[^0-9]", "")
                .replaceFirst("^0+(?!$)", "");
    }

    private enum ColumnKey {
        QUANTITY,
        NAME,
        SET_CODE,
        SET_NAME,
        COLLECTOR_NUMBER,
        CONDITION,
        PRINTING,
        LANGUAGE,
        LOCAL_PRICE,
        CK_PRICE_USD,
        ACTION
    }

    private record SheetColumns(EnumMap<ColumnKey, Integer> indexes, int width) {

        private static SheetColumns fromHeader(List<Object> header) {
            EnumMap<ColumnKey, Integer> indexes = defaults().indexes();
            Map<String, Integer> normalizedHeader = new HashMap<>();

            for (int index = 0; index < header.size(); index++) {
                normalizedHeader.put(normalizeHeader(header.get(index).toString()), index);
            }

            for (ColumnKey key : ColumnKey.values()) {
                for (String alias : aliases(key)) {
                    Integer index = normalizedHeader.get(normalizeHeader(alias));
                    if (index != null) {
                        indexes.put(key, index);
                        break;
                    }
                }
            }

            return new SheetColumns(indexes, Math.max(header.size(), 11));
        }

        private static SheetColumns defaults() {
            EnumMap<ColumnKey, Integer> indexes = new EnumMap<>(ColumnKey.class);
            indexes.put(ColumnKey.QUANTITY, 0);
            indexes.put(ColumnKey.NAME, 1);
            indexes.put(ColumnKey.SET_CODE, 2);
            indexes.put(ColumnKey.SET_NAME, 3);
            indexes.put(ColumnKey.COLLECTOR_NUMBER, 4);
            indexes.put(ColumnKey.CONDITION, 5);
            indexes.put(ColumnKey.PRINTING, 6);
            indexes.put(ColumnKey.LANGUAGE, 7);
            indexes.put(ColumnKey.LOCAL_PRICE, 8);
            indexes.put(ColumnKey.CK_PRICE_USD, 9);
            indexes.put(ColumnKey.ACTION, 10);
            return new SheetColumns(indexes, 11);
        }

        private String get(List<Object> row, ColumnKey key) {
            int index = index(key);
            if (index < 0 || index >= row.size()) {
                return "";
            }

            return row.get(index).toString();
        }

        private int index(ColumnKey key) {
            return indexes.getOrDefault(key, -1);
        }

        private boolean has(ColumnKey key) {
            return index(key) >= 0;
        }

        private String cell(ColumnKey key, int rowIndex) {
            return columnLetter(index(key)) + rowIndex;
        }

        private static List<String> aliases(ColumnKey key) {
            List<String> aliases = switch (key) {
                case QUANTITY -> List.of("Cantidad", "Stock", "Qty", "Quantity");
                case NAME -> List.of("Nombre", "Carta", "Card", "Name");
                case SET_CODE -> List.of("Codigo de set", "Codigo set", "Set code", "SetCode", "Code");
                case SET_NAME -> List.of("Nombre del set", "Nombre set", "Set", "Edition", "Edicion");
                case COLLECTOR_NUMBER -> List.of("Numero de carta", "Numero carta", "Numero", "N", "No", "Number", "Collector number", "Collector");
                case CONDITION -> List.of("Condicion", "Condition", "Estado carta");
                case PRINTING -> List.of("Printing", "Foil", "Nonfoil", "Printing foil/no foil", "Printing (foil/no foil)");
                case LANGUAGE -> List.of("Idioma", "Language", "Lang");
                case LOCAL_PRICE -> List.of("Precio Local", "Precio Local ARS", "Precio local", "Precio local ARS", "Precio", "ARS", "Price");
                case CK_PRICE_USD -> List.of("Precio CK USD", "CK USD", "Card Kingdom USD", "CK Price", "USD");
                case ACTION -> List.of("Accion", "Action", "Estado", "Stock status");
            };

            Set<String> expanded = new LinkedHashSet<>(aliases);
            for (String alias : aliases) {
                expanded.add(alias.replace("Codigo", "Código"));
                expanded.add(alias.replace("Numero", "Número"));
                expanded.add(alias.replace("Condicion", "Condición"));
                expanded.add(alias.replace("Accion", "Acción"));
                expanded.add(alias.replace("Edicion", "Edición"));
            }

            return List.copyOf(expanded);
        }

        private static String columnLetter(int index) {
            int value = index + 1;
            StringBuilder letter = new StringBuilder();

            while (value > 0) {
                value--;
                letter.insert(0, (char) ('A' + (value % 26)));
                value /= 26;
            }

            return letter.toString();
        }

        private static String normalizeHeader(String value) {
            String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "");

            return normalized.toLowerCase()
                    .replaceAll("[^a-z0-9]+", " ")
                    .trim()
                    .replaceAll("\\s+", " ");
        }
    }
}
