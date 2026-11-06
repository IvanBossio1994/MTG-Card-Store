package com.tcg.bot.service;

import com.tcg.bot.i18n.I18n;
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
    private static final long MAX_CREDENTIALS_BYTES = 32L * 1024L;

    /** Cómo se usa el logo de fondo: nada, una marca de agua grande en el centro, o un mosaico chico. */
    public static final String BACKDROP_NONE = "none";
    public static final String BACKDROP_WATERMARK = "watermark";
    public static final String BACKDROP_PATTERN = "pattern";
    private static final java.util.Set<String> BACKDROP_STYLES =
            java.util.Set.of(BACKDROP_NONE, BACKDROP_WATERMARK, BACKDROP_PATTERN);
    public static final int MIN_BACKDROP_INTENSITY = 1;
    public static final int MAX_BACKDROP_INTENSITY = 10;
    private static final int DEFAULT_BACKDROP_INTENSITY = 4;

    private final String defaultSpreadsheetId;
    private final Path configDirectory;
    private final Path configFile;

    private static final String LEGACY_DEFAULT_NAME = "Inventory Manager";

    private volatile String storeName = "";
    private volatile String spreadsheetId;
    private volatile String inventorySheetName = "Inventario";
    private volatile String cacheDirectory;
    private volatile String logoFilename = "";
    private volatile boolean tutorialCompleted = false;
    private volatile String currency = com.tcg.bot.i18n.Money.DEFAULT_CURRENCY;
    private volatile String backdropStyle = BACKDROP_WATERMARK;
    private volatile int backdropIntensity = DEFAULT_BACKDROP_INTENSITY;
    private volatile boolean useLogoColors = false;
    private volatile String logoAccent = "";

    public StoreSettingsService(
            @Value("${store.default-spreadsheet-id}") String defaultSpreadsheetId,
            @Value("${app.storage-dir:./data}") String storageDirectory
    ) {
        this.defaultSpreadsheetId = defaultSpreadsheetId;
        this.configDirectory = Paths.get(storageDirectory);
        this.configFile = configDirectory.resolve("store.properties");
        this.spreadsheetId = defaultSpreadsheetId;
        this.cacheDirectory = configDirectory.resolve("cache").toString();
        load();
        com.tcg.bot.i18n.Money.setCurrency(currency);
    }

    /** Nombre de la tienda; si nunca se eligió, "Mi tienda" en el idioma de la app. */
    public String getStoreName() {
        return storeName == null || storeName.isBlank() ? com.tcg.bot.i18n.I18n.t("store.defaultName") : storeName;
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

    public Path getGoogleCredentialsPath() {
        return configDirectory.resolve("google-credentials.json");
    }

    public boolean hasGoogleCredentials() {
        return Files.exists(getGoogleCredentialsPath());
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

    /** Cambia cada vez que se sube un logo nuevo, para que el navegador no muestre el anterior guardado. */
    public long getLogoVersion() {
        return getLogoPath().map(path -> {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }).orElse(0L);
    }

    public String getBackdropStyle() {
        return backdropStyle;
    }

    /** De 1 (casi invisible) a 10 (bien marcado). */
    public int getBackdropIntensity() {
        return backdropIntensity;
    }

    public boolean isUseLogoColors() {
        return useLogoColors;
    }

    /** Color principal del logo tal como lo calculó el navegador ("#rrggbb" o ""). */
    public String getLogoAccent() {
        return logoAccent;
    }

    /**
     * Decoración con el logo: fondo y colores. Lo que no se reconoce vuelve al valor por defecto, y la
     * intensidad se limita a 1..10, así un valor raro nunca tapa el contenido.
     */
    public synchronized void updateDecoration(String style, Integer intensity, boolean useColors, String accent)
            throws IOException {
        backdropStyle = normalizeBackdropStyle(style);
        backdropIntensity = normalizeBackdropIntensity(intensity);
        useLogoColors = useColors;
        if (accent != null) {
            logoAccent = StoreTheme.normalizeHex(accent);
        }
        save();
    }

    static String normalizeBackdropStyle(String style) {
        return style != null && BACKDROP_STYLES.contains(style) ? style : BACKDROP_WATERMARK;
    }

    static int normalizeBackdropIntensity(Integer intensity) {
        if (intensity == null) {
            return DEFAULT_BACKDROP_INTENSITY;
        }
        return Math.max(MIN_BACKDROP_INTENSITY, Math.min(MAX_BACKDROP_INTENSITY, intensity));
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
            throw new IllegalArgumentException(I18n.t("err.storeName.required"));
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
            throw new IllegalArgumentException(I18n.t("err.logo.size"));
        }

        logoExtension(logo);
    }

    /** Por el tipo que manda el navegador; si no lo manda (o manda uno genérico), por la extensión del archivo. */
    private String logoExtension(MultipartFile logo) {
        try {
            return extensionFor(logo.getContentType());
        } catch (IllegalArgumentException e) {
            String name = logo.getOriginalFilename() == null ? "" : logo.getOriginalFilename().toLowerCase(java.util.Locale.ROOT);
            if (name.endsWith(".png")) {
                return "png";
            }
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                return "jpg";
            }
            if (name.endsWith(".webp")) {
                return "webp";
            }
            throw e;
        }
    }

    public void validateGoogleCredentials(MultipartFile credentialsFile) {
        if (credentialsFile == null || credentialsFile.isEmpty()) {
            return;
        }

        if (credentialsFile.getSize() > MAX_CREDENTIALS_BYTES) {
            throw new IllegalArgumentException(I18n.t("err.credentials.size"));
        }

        String filename = credentialsFile.getOriginalFilename() == null
                ? ""
                : credentialsFile.getOriginalFilename().toLowerCase();

        if (!filename.endsWith(".json")) {
            throw new IllegalArgumentException(I18n.t("err.credentials.json"));
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

    /** Usa un Sheet recien creado por la app en el Drive de la cuenta conectada. */
    public synchronized void useSpreadsheet(String newSpreadsheetId) throws IOException {
        spreadsheetId = extractSpreadsheetId(newSpreadsheetId);
        save();
    }

    public synchronized void useInventorySheetName(String sheetName) throws IOException {
        validateSheetName(sheetName);
        inventorySheetName = sheetName.trim();
        save();
    }

    public String getCurrency() {
        return currency;
    }

    /** Moneda en la que cobra la tienda (ARS, USD, EUR...). Solo cambia como se muestran los precios. */
    public synchronized void updateCurrency(String code) throws IOException {
        currency = com.tcg.bot.i18n.Money.normalize(code);
        com.tcg.bot.i18n.Money.setCurrency(currency);
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
        String filename = "store-logo." + logoExtension(logo);

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

    public synchronized void saveGoogleCredentials(MultipartFile credentialsFile) throws IOException {
        if (credentialsFile == null || credentialsFile.isEmpty()) {
            return;
        }

        validateGoogleCredentials(credentialsFile);
        Files.createDirectories(configDirectory);

        try (InputStream input = credentialsFile.getInputStream()) {
            Files.copy(input, getGoogleCredentialsPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public synchronized void removeLogo() throws IOException {
        if (!logoFilename.isBlank()) {
            Files.deleteIfExists(configDirectory.resolve(logoFilename));
        }

        logoFilename = "";
        logoAccent = "";
        save();
    }

    private void validateSheetName(String sheetName) {
        if (sheetName == null || sheetName.isBlank()) {
            throw new IllegalArgumentException(I18n.t("err.sheetName.required"));
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
            throw new IllegalArgumentException(I18n.t("err.cache.invalid"));
        }
    }

    private String extractSpreadsheetId(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException(I18n.t("err.sheet.required"));
        }

        String value = reference.trim();
        Matcher urlMatcher = SHEET_URL_PATTERN.matcher(value);
        if (urlMatcher.find()) {
            return urlMatcher.group(1);
        }

        if (SHEET_ID_PATTERN.matcher(value).matches()) {
            return value;
        }

        throw new IllegalArgumentException(I18n.t("err.sheet.invalid"));
    }

    private String extensionFor(String contentType) {
        if ("image/png".equalsIgnoreCase(contentType)) {
            return "png";
        }
        if ("image/jpeg".equalsIgnoreCase(contentType) || "image/jpg".equalsIgnoreCase(contentType)
                || "image/pjpeg".equalsIgnoreCase(contentType)) {
            return "jpg";
        }
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return "webp";
        }
        throw new IllegalArgumentException(I18n.t("err.logo.type"));
    }

    private void load() {
        if (!Files.exists(configFile)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(configFile)) {
            properties.load(input);
            storeName = properties.getProperty("store.name", storeName);
            if (LEGACY_DEFAULT_NAME.equals(storeName)) {
                // Nombre en inglés que traían las versiones viejas sin que nadie lo eligiera.
                storeName = "";
            }
            spreadsheetId = properties.getProperty("store.spreadsheet-id", defaultSpreadsheetId);
            inventorySheetName = properties.getProperty("store.inventory-sheet-name", "Inventario");
            cacheDirectory = properties.getProperty("store.cache-directory", configDirectory.resolve("cache").toString());
            logoFilename = properties.getProperty("store.logo-filename", "");
            tutorialCompleted = Boolean.parseBoolean(properties.getProperty("tutorial.completed", "false"));
            currency = com.tcg.bot.i18n.Money.normalize(properties.getProperty("store.currency", com.tcg.bot.i18n.Money.DEFAULT_CURRENCY));
            backdropStyle = normalizeBackdropStyle(properties.getProperty("store.backdrop-style", BACKDROP_WATERMARK));
            backdropIntensity = parseIntensity(properties.getProperty("store.backdrop-intensity"));
            useLogoColors = Boolean.parseBoolean(properties.getProperty("store.use-logo-colors", "false"));
            logoAccent = StoreTheme.normalizeHex(properties.getProperty("store.logo-accent", ""));
        } catch (IOException e) {
            storeName = "";
            spreadsheetId = defaultSpreadsheetId;
            inventorySheetName = "Inventario";
            cacheDirectory = configDirectory.resolve("cache").toString();
            logoFilename = "";
            tutorialCompleted = false;
        }
    }

    private static int parseIntensity(String value) {
        try {
            return normalizeBackdropIntensity(value == null ? null : Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_BACKDROP_INTENSITY;
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
        properties.setProperty("store.currency", currency);
        properties.setProperty("store.backdrop-style", backdropStyle);
        properties.setProperty("store.backdrop-intensity", String.valueOf(backdropIntensity));
        properties.setProperty("store.use-logo-colors", String.valueOf(useLogoColors));
        properties.setProperty("store.logo-accent", logoAccent);

        try (OutputStream output = Files.newOutputStream(configFile)) {
            properties.store(output, "Configuracion local de la tienda");
        }
    }
}
