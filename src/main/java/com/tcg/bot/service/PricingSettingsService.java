package com.tcg.bot.service;

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
    private final int defaultRoundMultiple;
    private final Path settingsFile;

    private volatile double ckDollarRate;
    private volatile int roundMultiple;
    private volatile double markupPercent;
    private volatile double minimumPrice;

    public PricingSettingsService(
            @Value("${pricing.ck-dollar-rate:1650.0}") double defaultCkDollarRate,
            @Value("${pricing.round-multiple:500}") int defaultRoundMultiple,
            @Value("${app.storage-dir:D:/TCG-inventory/data}") String storageDirectory
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

    public int getRoundMultiple() {
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

    public synchronized void update(double newCkDollarRate, int newRoundMultiple) throws IOException {
        update(newCkDollarRate, newRoundMultiple, markupPercent, minimumPrice);
    }

    public synchronized void update(
            double newCkDollarRate,
            int newRoundMultiple,
            double newMarkupPercent,
            double newMinimumPrice
    ) throws IOException {
        if (newMarkupPercent <= -100) {
            throw new IllegalArgumentException("El recargo tiene que ser mayor a -100%.");
        }

        if (newMinimumPrice < 0) {
            throw new IllegalArgumentException("El precio minimo no puede ser negativo.");
        }

        if (newCkDollarRate <= 0) {
            throw new IllegalArgumentException("La cotizacion debe ser mayor a cero.");
        }

        if (newRoundMultiple <= 0) {
            throw new IllegalArgumentException("El multiplo debe ser mayor a cero.");
        }

        Properties properties = new Properties();
        properties.setProperty(DOLLAR_RATE_KEY, Double.toString(newCkDollarRate));
        properties.setProperty(ROUND_MULTIPLE_KEY, Integer.toString(newRoundMultiple));
        properties.setProperty(MARKUP_PERCENT_KEY, Double.toString(newMarkupPercent));
        properties.setProperty(MINIMUM_PRICE_KEY, Double.toString(newMinimumPrice));

        Files.createDirectories(settingsFile.getParent());

        try (OutputStream outputStream = Files.newOutputStream(settingsFile)) {
            properties.store(outputStream, "Regla de precio configurada desde la aplicacion");
        }

        ckDollarRate = newCkDollarRate;
        roundMultiple = newRoundMultiple;
        markupPercent = newMarkupPercent;
        minimumPrice = newMinimumPrice;
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
            roundMultiple = parsePositiveInt(
                    properties.getProperty(ROUND_MULTIPLE_KEY),
                    defaultRoundMultiple
            );
            markupPercent = parseDouble(properties.getProperty(MARKUP_PERCENT_KEY), 0);
            minimumPrice = Math.max(parseDouble(properties.getProperty(MINIMUM_PRICE_KEY), 0), 0);
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

    private int parsePositiveInt(String value, int fallback) {
        try {
            int parsedValue = Integer.parseInt(value);
            return parsedValue > 0 ? parsedValue : fallback;
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }
}
