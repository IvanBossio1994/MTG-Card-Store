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
import java.time.LocalTime;
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
    private volatile String cacheDirectory;
    private volatile String logoFilename = "";
    private volatile boolean tutorialCompleted = false;
    private volatile boolean modulesTutorialCompleted = false;
    private volatile boolean whatsappEnabled = false;
    private volatile String whatsappPhoneNumberId = "";
    private volatile String whatsappAccessToken = "";
    private volatile String whatsappVerifyToken = "";
    private volatile boolean whatsappAlwaysOn = true;
    private volatile String whatsappOpeningTime = "10:00";
    private volatile String whatsappClosingTime = "20:00";

    public StoreSettingsService(
            @Value("${store.default-spreadsheet-id}") String defaultSpreadsheetId,
            @Value("${app.storage-dir:D:/TCG-inventory/data}") String storageDirectory
    ) {
        this.defaultSpreadsheetId = defaultSpreadsheetId;
        this.configDirectory = Paths.get(storageDirectory);
        this.configFile = configDirectory.resolve("store.properties");
        this.spreadsheetId = defaultSpreadsheetId;
        this.cacheDirectory = configDirectory.resolve("cache").toString();
        load();
    }

    public String getStoreName() {
        return storeName;
    }

    public String getSpreadsheetId() {
        return spreadsheetId;
    }

    public boolean hasSpreadsheetConfigured() {
        return spreadsheetId != null && !spreadsheetId.isBlank();
    }

    public String getInventorySource() {
        return SOURCE_GOOGLE_SHEET;
    }

    public String getInventorySheetName() {
        return inventorySheetName;
    }

    public String getCacheDirectory() {
        return cacheDirectory;
    }

    public Path getCardKingdomCachePath() {
        return Paths.get(cacheDirectory).resolve("ck-pricelist.json");
    }

    public Path getGoogleOAuthTokenDirectory() {
        return configDirectory.resolve("google-oauth-token");
    }

    public boolean isTutorialCompleted() {
        return tutorialCompleted;
    }

    public boolean isModulesTutorialCompleted() {
        return modulesTutorialCompleted;
    }

    public boolean hasLogo() {
        return getLogoPath().isPresent();
    }

    public boolean isWhatsappEnabled() {
        return whatsappEnabled;
    }

    public String getWhatsappPhoneNumberId() {
        return whatsappPhoneNumberId;
    }

    public String getWhatsappAccessToken() {
        return whatsappAccessToken;
    }

    public String getWhatsappVerifyToken() {
        return whatsappVerifyToken;
    }

    public boolean isWhatsappAlwaysOn() {
        return whatsappAlwaysOn;
    }

    public String getWhatsappOpeningTime() {
        return whatsappOpeningTime;
    }

    public String getWhatsappClosingTime() {
        return whatsappClosingTime;
    }

    public boolean hasWhatsappConfigured() {
        return whatsappEnabled
                && !whatsappPhoneNumberId.isBlank()
                && !whatsappAccessToken.isBlank()
                && !whatsappVerifyToken.isBlank();
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
            String sheetName,
            String cacheDirectory
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Ingresa el nombre de la tienda.");
        }

        validateSheetName(sheetName);
        validateCacheDirectory(cacheDirectory);
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

    public void validateWhatsappSettings(
            boolean enabled,
            String phoneNumberId,
            String accessToken,
            String verifyToken,
            boolean alwaysOn,
            String openingTime,
            String closingTime
    ) {
        if (!enabled) {
            return;
        }

        if (phoneNumberId == null || phoneNumberId.isBlank()) {
            throw new IllegalArgumentException("Ingresa el Phone Number ID de WhatsApp.");
        }

        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Ingresa el Access Token de WhatsApp.");
        }

        if (verifyToken == null || verifyToken.isBlank()) {
            throw new IllegalArgumentException("Ingresa el Verify Token de WhatsApp.");
        }

        if (!alwaysOn) {
            parseTime(openingTime, "hora de apertura");
            parseTime(closingTime, "hora de cierre");
        }
    }

    public synchronized void update(
            String name,
            String sheetReference,
            String sheetName,
            String cacheDirectory
    ) throws IOException {
        storeName = name.trim();
        spreadsheetId = extractSpreadsheetId(sheetReference);
        inventorySheetName = sheetName.trim();
        this.cacheDirectory = normalizeCacheDirectory(cacheDirectory);
        save();
    }

    public synchronized void updateWhatsapp(
            boolean enabled,
            String phoneNumberId,
            String accessToken,
            String verifyToken,
            boolean alwaysOn,
            String openingTime,
            String closingTime
    ) throws IOException {
        validateWhatsappSettings(enabled, phoneNumberId, accessToken, verifyToken, alwaysOn, openingTime, closingTime);
        whatsappEnabled = enabled;
        whatsappPhoneNumberId = blankToEmpty(phoneNumberId);
        whatsappAccessToken = blankToEmpty(accessToken);
        whatsappVerifyToken = blankToEmpty(verifyToken);
        whatsappAlwaysOn = alwaysOn;
        whatsappOpeningTime = normalizedTime(openingTime, "10:00");
        whatsappClosingTime = normalizedTime(closingTime, "20:00");
        save();
    }

    public synchronized void useInventorySheetName(String sheetName) throws IOException {
        validateSheetName(sheetName);
        inventorySheetName = sheetName.trim();
        save();
    }

    public synchronized void completeTutorial() throws IOException {
        tutorialCompleted = true;
        save();
    }

    public synchronized void completeModulesTutorial() throws IOException {
        modulesTutorialCompleted = true;
        save();
    }

    public synchronized void resetTutorial() throws IOException {
        tutorialCompleted = false;
        modulesTutorialCompleted = false;
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

    private void validateCacheDirectory(String cacheDirectory) {
        normalizeCacheDirectory(cacheDirectory);
    }

    private String normalizeCacheDirectory(String value) {
        if (value == null || value.isBlank()) {
            return configDirectory.resolve("cache").toString();
        }

        try {
            return Paths.get(value.trim()).normalize().toString();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("La carpeta de cache no es valida.");
        }
    }

    private String normalizedTime(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return parseTime(value, "horario").toString();
    }

    private LocalTime parseTime(String value, String label) {
        try {
            return LocalTime.parse(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("La " + label + " de WhatsApp no es valida.");
        }
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
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
            cacheDirectory = properties.getProperty("store.cache-directory", configDirectory.resolve("cache").toString());
            logoFilename = properties.getProperty("store.logo-filename", "");
            tutorialCompleted = Boolean.parseBoolean(properties.getProperty("tutorial.completed", "false"));
            modulesTutorialCompleted = Boolean.parseBoolean(properties.getProperty("tutorial.modules.completed", "false"));
            whatsappEnabled = Boolean.parseBoolean(properties.getProperty("whatsapp.enabled", "false"));
            whatsappPhoneNumberId = properties.getProperty("whatsapp.phone-number-id", "");
            whatsappAccessToken = properties.getProperty("whatsapp.access-token", "");
            whatsappVerifyToken = properties.getProperty("whatsapp.verify-token", "");
            whatsappAlwaysOn = Boolean.parseBoolean(properties.getProperty("whatsapp.always-on", "true"));
            whatsappOpeningTime = properties.getProperty("whatsapp.opening-time", "10:00");
            whatsappClosingTime = properties.getProperty("whatsapp.closing-time", "20:00");
        } catch (IOException e) {
            storeName = "Inventory Manager";
            spreadsheetId = defaultSpreadsheetId;
            inventorySheetName = "Inventario";
            cacheDirectory = configDirectory.resolve("cache").toString();
            logoFilename = "";
            tutorialCompleted = false;
            modulesTutorialCompleted = false;
            whatsappEnabled = false;
            whatsappPhoneNumberId = "";
            whatsappAccessToken = "";
            whatsappVerifyToken = "";
            whatsappAlwaysOn = true;
            whatsappOpeningTime = "10:00";
            whatsappClosingTime = "20:00";
        }
    }

    private void save() throws IOException {
        Files.createDirectories(configDirectory);

        Properties properties = new Properties();
        properties.setProperty("store.name", storeName);
        properties.setProperty("store.inventory-source", SOURCE_GOOGLE_SHEET);
        properties.setProperty("store.spreadsheet-id", spreadsheetId);
        properties.setProperty("store.inventory-sheet-name", inventorySheetName);
        properties.setProperty("store.cache-directory", cacheDirectory);
        properties.setProperty("store.logo-filename", logoFilename);
        properties.setProperty("tutorial.completed", String.valueOf(tutorialCompleted));
        properties.setProperty("tutorial.modules.completed", String.valueOf(modulesTutorialCompleted));
        properties.setProperty("whatsapp.enabled", String.valueOf(whatsappEnabled));
        properties.setProperty("whatsapp.phone-number-id", whatsappPhoneNumberId);
        properties.setProperty("whatsapp.access-token", whatsappAccessToken);
        properties.setProperty("whatsapp.verify-token", whatsappVerifyToken);
        properties.setProperty("whatsapp.always-on", String.valueOf(whatsappAlwaysOn));
        properties.setProperty("whatsapp.opening-time", whatsappOpeningTime);
        properties.setProperty("whatsapp.closing-time", whatsappClosingTime);

        try (OutputStream output = Files.newOutputStream(configFile)) {
            properties.store(output, "Configuracion local de la tienda");
        }
    }
}
