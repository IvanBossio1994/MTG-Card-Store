package com.tcg.bot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StoreSettingsService {

    public static final String SOURCE_GOOGLE_SHEET = "GOOGLE_SHEET";

    private static final Pattern SHEET_URL_PATTERN =
            Pattern.compile("https?://docs\\.google\\.com/spreadsheets/d/([A-Za-z0-9_-]+)");

    private static final Pattern SHEET_ID_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]+");

    private static final long MAX_LOGO_BYTES = 2L * 1024L * 1024L;

    private final String defaultSpreadsheetId;
    private final Path configDirectory;
    private final Path configFile;

    private volatile String storeName = "Inventory Manager";
    private volatile String spreadsheetId;
    private volatile String inventorySheetName = "Inventario";
    private volatile String logoFilename = "";
    private volatile boolean tutorialCompleted = false;

    public StoreSettingsService(
            @Value("${store.default-spreadsheet-id}") String defaultSpreadsheetId,
            @Value("${app.storage-dir:D:/TCG-inventory/data}") String storageDirectory
    ) {
        this.defaultSpreadsheetId = defaultSpreadsheetId;
        this.configDirectory = Paths.get(storageDirectory);
        this.configFile = configDirectory.resolve("store.properties");
        this.spreadsheetId = defaultSpreadsheetId;
        load();
    }

    public String getStoreName() {
        return storeName;
    }

    public String getSpreadsheetId() {
        return spreadsheetId;
    }

    public String getInventorySource() {
        return SOURCE_GOOGLE_SHEET;
    }

    public String getInventorySheetName() {
        return inventorySheetName;
    }

    public boolean isTutorialCompleted() {
        return tutorialCompleted;
    }

    public boolean hasLogo() {
        return getLogoPath().isPresent();
    }

    public Optional<Path> getLogoPath() {
        if (logoFilename.isBlank()) {
            return Optional.empty();
        }

        Path logoPath = configDirectory.resolve(logoFilename);
        return Files.exists(logoPath) ? Optional.of(logoPath) : Optional.empty();
    }

    public String getLogoContentType() {
        if (logoFilename.endsWith(".png")) {
            return "image/png";
        }
        if (logoFilename.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/jpeg";
    }

    public void validateSettings(
            String name,
            String sheetReference,
            String sheetName
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ingresa el nombre de la tienda.");
        }

        validateSheetName(sheetName);
        extractSpreadsheetId(sheetReference);
    }

    public void validateLogo(MultipartFile logo) {
        if (logo == null || logo.isEmpty()) {
            return;
        }

        if (logo.getSize() > MAX_LOGO_BYTES) {
            throw new IllegalArgumentException("El logo no puede superar los 2 MB.");
        }

        extensionFor(logo.getContentType());
    }

    public synchronized void update(
            String name,
            String sheetReference,
            String sheetName
    ) throws IOException {
        storeName = name.trim();
        spreadsheetId = extractSpreadsheetId(sheetReference);
        inventorySheetName = sheetName.trim();
        save();
    }

    public synchronized void completeTutorial() throws IOException {
        tutorialCompleted = true;
        save();
    }

    public synchronized void resetTutorial() throws IOException {
        tutorialCompleted = false;
        save();
    }

    public synchronized void saveLogo(MultipartFile logo) throws IOException {
        if (logo == null || logo.isEmpty()) {
            return;
        }

        validateLogo(logo);
        String filename = "store-logo." + extensionFor(logo.getContentType());

        Files.createDirectories(configDirectory);
        try (InputStream input = logo.getInputStream()) {
            Files.copy(input, configDirectory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        }

        if (!logoFilename.isBlank() && !logoFilename.equals(filename)) {
            Files.deleteIfExists(configDirectory.resolve(logoFilename));
        }

        logoFilename = filename;
        save();
    }

    public synchronized void removeLogo() throws IOException {
        if (!logoFilename.isBlank()) {
            Files.deleteIfExists(configDirectory.resolve(logoFilename));
        }

        logoFilename = "";
        save();
    }

    private void validateSheetName(String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            throw new IllegalArgumentException("Ingresa el nombre de la hoja que contiene el inventario.");
        }
    }

    private String extractSpreadsheetId(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Ingresa el enlace o ID del Google Sheet.");
        }

        String value = reference.trim();
        Matcher urlMatcher = SHEET_URL_PATTERN.matcher(value);
        if (urlMatcher.find()) {
            return urlMatcher.group(1);
        }

        if (SHEET_ID_PATTERN.matcher(value).matches()) {
            return value;
        }

        throw new IllegalArgumentException("El enlace o ID del Google Sheet no es valido.");
    }

    private String extensionFor(String contentType) {
        if ("image/png".equalsIgnoreCase(contentType)) {
            return "png";
        }
        if ("image/jpeg".equalsIgnoreCase(contentType)) {
            return "jpg";
        }
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return "webp";
        }
        throw new IllegalArgumentException("El logo debe ser una imagen PNG, JPG o WEBP.");
    }

    private void load() {
        if (!Files.exists(configFile)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configFile)) {
            properties.load(input);
            storeName = properties.getProperty("store.name", storeName);
            spreadsheetId = properties.getProperty("store.spreadsheet-id", defaultSpreadsheetId);
            inventorySheetName = properties.getProperty("store.inventory-sheet-name", "Inventario");
            logoFilename = properties.getProperty("store.logo-filename", "");
            tutorialCompleted = Boolean.parseBoolean(properties.getProperty("tutorial.completed", "false"));
        } catch (IOException e) {
            storeName = "Inventory Manager";
            spreadsheetId = defaultSpreadsheetId;
            inventorySheetName = "Inventario";
            logoFilename = "";
            tutorialCompleted = false;
        }
    }

    private void save() throws IOException {
        Files.createDirectories(configDirectory);

        Properties properties = new Properties();
        properties.setProperty("store.name", storeName);
        properties.setProperty("store.inventory-source", SOURCE_GOOGLE_SHEET);
        properties.setProperty("store.spreadsheet-id", spreadsheetId);
        properties.setProperty("store.inventory-sheet-name", inventorySheetName);
        properties.setProperty("store.logo-filename", logoFilename);
        properties.setProperty("tutorial.completed", String.valueOf(tutorialCompleted));

        try (OutputStream output = Files.newOutputStream(configFile)) {
            properties.store(output, "Configuracion local de la tienda");
        }
    }
}
