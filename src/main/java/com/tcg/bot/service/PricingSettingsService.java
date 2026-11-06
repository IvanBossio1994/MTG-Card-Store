package com.tcg.bot.service;

import com.tcg.bot.i18n.I18n;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

@Service
public class PricingSettingsService {

    private static final String DOLLAR_RATE_KEY =
            "pricing.ck-dollar-rate";

    private static final String ROUND_MULTIPLE_KEY =
            "pricing.round-multiple";

    private static final String MARKUP_PERCENT_KEY =
            "pricing.markup-percent";

    private static final String MINIMUM_PRICE_KEY =
            "pricing.minimum-price";

    private final double defaultCkDollarRate;
    private final double defaultRoundMultiple;
    private final Path settingsFile;

    private volatile double ckDollarRate;
    private volatile double roundMultiple;
    private volatile double markupPercent;
    private volatile double minimumPrice;
    private volatile boolean tcgplayerEnabled;
    private volatile boolean cardmarketEnabled;
    private volatile double eurRate;
    private volatile CombineMode combineMode = CombineMode.CARD_KINGDOM;

    /** Como se combinan las fuentes activas para sugerir el precio local. */
    public enum CombineMode {
        CARD_KINGDOM("Solo Card Kingdom"),
        AVERAGE("Promedio de las fuentes"),
        MAX("La más alta"),
        MIN("La más baja");

        private final String label;

        CombineMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static CombineMode from(String value) {
            for (CombineMode mode : values()) {
                if (mode.name().equalsIgnoreCase(value)) {
                    return mode;
                }
            }
            return CARD_KINGDOM;
        }
    }

    public PricingSettingsService(
            @Value("${pricing.ck-dollar-rate:1650.0}") double defaultCkDollarRate,
            @Value("${pricing.round-multiple:500}") double defaultRoundMultiple,
            @Value("${app.storage-dir:./data}") String storageDirectory
    ) {
        this.defaultCkDollarRate = defaultCkDollarRate;
        this.defaultRoundMultiple = defaultRoundMultiple;
        this.settingsFile = Paths.get(storageDirectory).resolve("pricing.properties");
        this.ckDollarRate = defaultCkDollarRate;
        this.roundMultiple = defaultRoundMultiple;
        load();
    }

    public double getCkDollarRate() {
        return ckDollarRate;
    }

    public double getRoundMultiple() {
        return roundMultiple;
    }

    /** Recargo sobre el precio de referencia, en porcentaje. Puede ser negativo para vender por debajo. */
    public double getMarkupPercent() {
        return markupPercent;
    }

    /** Precio minimo en ARS por carta (por ejemplo, para que una comun no salga $0). 0 = sin minimo. */
    public double getMinimumPrice() {
        return minimumPrice;
    }

    public boolean isTcgplayerEnabled() {
        return tcgplayerEnabled;
    }

    public boolean isCardmarketEnabled() {
        return cardmarketEnabled;
    }

    /** ARS por euro, para convertir Cardmarket. */
    public double getEurRate() {
        return eurRate;
    }

    public CombineMode getCombineMode() {
        return combineMode;
    }

    public boolean usesExtraSources() {
        return tcgplayerEnabled || cardmarketEnabled;
    }

    public synchronized void updateSources(
            boolean newTcgplayerEnabled,
            boolean newCardmarketEnabled,
            double newEurRate,
            CombineMode newCombineMode
    ) throws IOException {
        if (newCardmarketEnabled && newEurRate <= 0) {
            throw new IllegalArgumentException(I18n.t("err.pricing.eur"));
        }

        tcgplayerEnabled = newTcgplayerEnabled;
        cardmarketEnabled = newCardmarketEnabled;
        eurRate = Math.max(newEurRate, 0);
        combineMode = newCombineMode == null ? CombineMode.CARD_KINGDOM : newCombineMode;
        update(ckDollarRate, roundMultiple, markupPercent, minimumPrice);
    }

    public synchronized void update(double newCkDollarRate, double newRoundMultiple) throws IOException {
        update(newCkDollarRate, newRoundMultiple, markupPercent, minimumPrice);
    }

    public synchronized void update(
            double newCkDollarRate,
            double newRoundMultiple,
            double newMarkupPercent,
            double newMinimumPrice
    ) throws IOException {
        if (newMarkupPercent <= -100) {
            throw new IllegalArgumentException(I18n.t("err.pricing.markup"));
        }

        if (newMinimumPrice < 0) {
            throw new IllegalArgumentException(I18n.t("err.pricing.minimum"));
        }

        if (newCkDollarRate <= 0) {
            throw new IllegalArgumentException(I18n.t("err.pricing.rate"));
        }

        if (newRoundMultiple <= 0) {
            throw new IllegalArgumentException(I18n.t("err.pricing.multiple"));
        }

        Properties properties = new Properties();
        properties.setProperty(DOLLAR_RATE_KEY, Double.toString(newCkDollarRate));
        properties.setProperty(ROUND_MULTIPLE_KEY, Double.toString(newRoundMultiple));
        properties.setProperty(MARKUP_PERCENT_KEY, Double.toString(newMarkupPercent));
        properties.setProperty(MINIMUM_PRICE_KEY, Double.toString(newMinimumPrice));
        properties.setProperty("pricing.source.tcgplayer", Boolean.toString(tcgplayerEnabled));
        properties.setProperty("pricing.source.cardmarket", Boolean.toString(cardmarketEnabled));
        properties.setProperty("pricing.eur-rate", Double.toString(eurRate));
        properties.setProperty("pricing.combine", combineMode.name());

        Files.createDirectories(settingsFile.getParent());

        try (OutputStream outputStream = Files.newOutputStream(settingsFile)) {
            properties.store(outputStream, "Regla de precio configurada desde la aplicacion");
        }

        ckDollarRate = newCkDollarRate;
        roundMultiple = newRoundMultiple;
        markupPercent = newMarkupPercent;
        minimumPrice = newMinimumPrice;
    }

    /** Redondeo sugerido para cada moneda: 500 pesos no son 500 dólares. */
    public static double suggestedRoundMultiple(String currency) {
        return switch (com.tcg.bot.i18n.Money.normalize(currency)) {
            case "USD", "EUR", "GBP", "CAD", "AUD" -> 0.25;
            case "BRL", "PEN" -> 0.5;
            case "MXN", "UYU", "JPY" -> 10;
            case "COP" -> 1000;
            default -> 500;
        };
    }

    private void load() {
        if (!Files.exists(settingsFile)) {
            return;
        }

        Properties properties = new Properties();

        try (InputStream inputStream = Files.newInputStream(settingsFile)) {
            properties.load(inputStream);
            ckDollarRate = parsePositiveDouble(
                    properties.getProperty(DOLLAR_RATE_KEY),
                    defaultCkDollarRate
            );
            roundMultiple = parsePositiveDouble(
                    properties.getProperty(ROUND_MULTIPLE_KEY),
                    defaultRoundMultiple
            );
            markupPercent = parseDouble(properties.getProperty(MARKUP_PERCENT_KEY), 0);
            minimumPrice = Math.max(parseDouble(properties.getProperty(MINIMUM_PRICE_KEY), 0), 0);
            tcgplayerEnabled = Boolean.parseBoolean(properties.getProperty("pricing.source.tcgplayer", "false"));
            cardmarketEnabled = Boolean.parseBoolean(properties.getProperty("pricing.source.cardmarket", "false"));
            eurRate = Math.max(parseDouble(properties.getProperty("pricing.eur-rate"), 0), 0);
            combineMode = CombineMode.from(properties.getProperty("pricing.combine"));
        } catch (IOException e) {
            ckDollarRate = defaultCkDollarRate;
            roundMultiple = defaultRoundMultiple;
        }
    }

    private double parsePositiveDouble(String value, double fallback) {
        try {
            double parsedValue = Double.parseDouble(value);
            return parsedValue > 0 ? parsedValue : fallback;
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }

    private double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }
}
