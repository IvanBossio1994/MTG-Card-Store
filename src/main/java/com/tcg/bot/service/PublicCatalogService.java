package com.tcg.bot.service;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.i18n.Money;
import com.tcg.bot.model.InventoryCard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Catálogo y buylist públicos: un Google Sheet aparte, de solo lectura para cualquiera con el link, que la tienda
 * comparte por WhatsApp o Instagram. Nunca se comparte el Sheet principal (tiene la caja, el crédito y los costos):
 * acá solo van nombre, edición, estado, precio y cantidad.
 * <ul>
 *     <li>Catálogo: lo que hay en stock con precio, con un código por carta para pedir sin confusiones.</li>
 *     <li>Buylist: lo que la tienda compra y cuánto paga (un % del precio de venta, en efectivo o en crédito),
 *     para las cartas con precio de al menos {@code minPrice} de las que tiene menos de {@code target} copias.</li>
 * </ul>
 */
@Service
public class PublicCatalogService {

    public record Settings(String spreadsheetId, int cashPercent, int creditPercent, int target, double minPrice,
                           String lastPublished) {
        public static final Settings DEFAULT = new Settings("", 50, 60, 4, 0, "");

        public boolean published() {
            return spreadsheetId != null && !spreadsheetId.isBlank();
        }

        public String link() {
            return published() ? "https://docs.google.com/spreadsheets/d/" + spreadsheetId + "/edit?usp=sharing" : "";
        }

        Settings withPublished(String id, String when) {
            return new Settings(id, cashPercent, creditPercent, target, minPrice, when);
        }
    }

    public record Result(String link, int catalogCards, int buylistCards) {
    }

    private final InventoryStore store;
    private final GoogleSheetsService sheets;
    private final StoreSettingsService storeSettings;
    private final StockOperationsService stockOperations;
    private final Path settingsFile;
    private volatile Settings settings = Settings.DEFAULT;

    public PublicCatalogService(InventoryStore store, GoogleSheetsService sheets, StoreSettingsService storeSettings,
                                StockOperationsService stockOperations,
                                @Value("${app.storage-dir:D:/TCG-inventory/data}") String storageDirectory) {
        this.store = store;
        this.sheets = sheets;
        this.storeSettings = storeSettings;
        this.stockOperations = stockOperations;
        this.settingsFile = Paths.get(storageDirectory).resolve("public-catalog.properties");
        load();
    }

    public Settings settings() {
        return settings;
    }

    /** Solo se puede publicar con una cuenta de Google conectada (la app crea el Sheet en ese Drive). */
    public boolean available() {
        return sheets.usesGoogleAccount();
    }

    public synchronized void saveSettings(int cashPercent, int creditPercent, int target, double minPrice) throws IOException {
        settings = new Settings(settings.spreadsheetId(), clamp(cashPercent, 0, 100), clamp(creditPercent, 0, 100),
                clamp(target, 1, 99), Math.max(0, minPrice), settings.lastPublished());
        save();
    }

    /** Arma las dos pestañas con el stock de ahora y las sube. Crea el Sheet público la primera vez. */
    public synchronized Result publish() throws Exception {
        if (!available()) {
            throw new IllegalStateException(I18n.t("public.err.needsGoogle"));
        }
        List<InventoryCard> cards = store.getAllStockCards();
        List<List<Object>> catalog = catalogRows(cards);
        List<List<Object>> buylist = buylistRows(cards, settings);

        Map<String, List<List<Object>>> tabs = new LinkedHashMap<>();
        tabs.put(I18n.t("public.tab.catalog"), catalog);
        tabs.put(I18n.t("public.tab.buylist"), buylist);
        String store = storeSettings.getStoreName();
        String title = I18n.t("public.sheetTitle", store == null || store.isBlank() ? "TCG" : store);

        String id = sheets.writePublicSpreadsheet(settings.spreadsheetId(), title, tabs);
        String when = LocalDateTime.now(stockOperations.clock).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        settings = settings.withPublished(id, when);
        save();
        return new Result(settings.link(), catalog.size() - 1, buylist.size() - 1);
    }

    /** Encabezado + una fila por carta en stock con precio, ordenadas por nombre. */
    static List<List<Object>> catalogRows(List<InventoryCard> cards) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(I18n.t("public.col.code"), I18n.t("public.col.card"), I18n.t("public.col.edition"),
                I18n.t("public.col.number"), I18n.t("public.col.foil"), I18n.t("public.col.condition"),
                I18n.t("public.col.language"), I18n.t("public.col.price"), I18n.t("public.col.stock")));
        for (InventoryCard card : sorted(cards)) {
            int stock = StockOperationsService.quantity(card);
            double price = card.localPriceValue();
            if (stock <= 0 || price <= 0) {
                continue;
            }
            rows.add(List.of(safe(card.getId()), card.getName().trim(), edition(card), safe(card.getCollectorNumber()),
                    card.isFoil() ? "Foil" : "", safe(card.getCondition()), safe(card.getLanguage()),
                    Money.format(price), stock));
        }
        return rows;
    }

    /**
     * Encabezado + una fila por carta que la tienda compra: tiene precio de al menos {@code minPrice} y menos de
     * {@code target} copias. "Compramos hasta" es cuántas faltan para llegar a {@code target}.
     */
    static List<List<Object>> buylistRows(List<InventoryCard> cards, Settings settings) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(I18n.t("public.col.card"), I18n.t("public.col.edition"), I18n.t("public.col.number"),
                I18n.t("public.col.foil"), I18n.t("public.col.payCash"), I18n.t("public.col.payCredit"),
                I18n.t("public.col.upTo")));
        // La misma carta puede estar en varias filas (estados o idiomas distintos): cuenta el stock junto.
        Map<String, Integer> stockByPrint = new LinkedHashMap<>();
        Map<String, InventoryCard> firstByPrint = new LinkedHashMap<>();
        for (InventoryCard card : sorted(cards)) {
            String key = (card.getGame() == null ? "" : card.getGame().slug()) + "|" + card.getName().trim().toLowerCase(Locale.ROOT)
                    + "|" + safe(card.getSetCode()).toLowerCase(Locale.ROOT) + "|" + safe(card.getCollectorNumber())
                    + "|" + card.isFoil();
            stockByPrint.merge(key, StockOperationsService.quantity(card), Integer::sum);
            InventoryCard first = firstByPrint.get(key);
            if (first == null || card.localPriceValue() > first.localPriceValue()) {
                firstByPrint.put(key, card);
            }
        }
        for (var entry : firstByPrint.entrySet()) {
            InventoryCard card = entry.getValue();
            double price = card.localPriceValue();
            int missing = settings.target() - Math.max(0, stockByPrint.get(entry.getKey()));
            if (price <= 0 || price < settings.minPrice() || missing <= 0) {
                continue;
            }
            rows.add(List.of(card.getName().trim(), edition(card), safe(card.getCollectorNumber()),
                    card.isFoil() ? "Foil" : "",
                    Money.format(Money.round(price * settings.cashPercent() / 100.0)),
                    Money.format(Money.round(price * settings.creditPercent() / 100.0)),
                    missing));
        }
        return rows;
    }

    private static List<InventoryCard> sorted(List<InventoryCard> cards) {
        return cards.stream()
                .filter(card -> card.getName() != null && !card.getName().isBlank())
                .sorted(Comparator.comparing((InventoryCard card) -> card.getName().trim(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PublicCatalogService::edition))
                .toList();
    }

    private static String edition(InventoryCard card) {
        String name = safe(card.getSetName());
        return name.isBlank() ? safe(card.getSetCode()).toUpperCase(Locale.ROOT) : name;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void load() {
        if (!Files.exists(settingsFile)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(settingsFile)) {
            properties.load(input);
            Settings d = Settings.DEFAULT;
            settings = new Settings(
                    properties.getProperty("spreadsheet-id", d.spreadsheetId()),
                    intOf(properties.getProperty("cash-percent"), d.cashPercent()),
                    intOf(properties.getProperty("credit-percent"), d.creditPercent()),
                    intOf(properties.getProperty("target"), d.target()),
                    Math.max(0, com.tcg.bot.util.Amounts.parse(properties.getProperty("min-price", "0"))),
                    properties.getProperty("last-published", d.lastPublished()));
        } catch (IOException e) {
            settings = Settings.DEFAULT;
        }
    }

    private void save() throws IOException {
        Files.createDirectories(settingsFile.getParent());
        Properties properties = new Properties();
        properties.setProperty("spreadsheet-id", settings.spreadsheetId());
        properties.setProperty("cash-percent", String.valueOf(settings.cashPercent()));
        properties.setProperty("credit-percent", String.valueOf(settings.creditPercent()));
        properties.setProperty("target", String.valueOf(settings.target()));
        properties.setProperty("min-price", Money.plain(settings.minPrice()));
        properties.setProperty("last-published", settings.lastPublished());
        try (OutputStream output = Files.newOutputStream(settingsFile)) {
            properties.store(output, "Catalogo y buylist publicos");
        }
    }

    private static int intOf(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
