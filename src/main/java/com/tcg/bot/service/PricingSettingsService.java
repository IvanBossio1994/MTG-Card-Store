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

    private final double defaultCkDollarRate;
    private final int defaultRoundMultiple;
    private final Path settingsFile;

    private volatile double ckDollarRate;
    private volatile int roundMultiple;

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

    public synchronized void update(double newCkDollarRate, int newRoundMultiple) throws IOException {
        if (newCkDollarRate <= 0) {
            throw new IllegalArgumentException("La cotizacion debe ser mayor a cero.");
        }

        if (newRoundMultiple <= 0) {
            throw new IllegalArgumentException("El multiplo debe ser mayor a cero.");
        }

        Properties properties = new Properties();
        properties.setProperty(DOLLAR_RATE_KEY, Double.toString(newCkDollarRate));
        properties.setProperty(ROUND_MULTIPLE_KEY, Integer.toString(newRoundMultiple));

        Files.createDirectories(settingsFile.getParent());

        try (OutputStream outputStream = Files.newOutputStream(settingsFile)) {
            properties.store(outputStream, "Regla de precio configurada desde la aplicacion");
        }

        ckDollarRate = newCkDollarRate;
        roundMultiple = newRoundMultiple;
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

    private int parsePositiveInt(String value, int fallback) {
        try {
            int parsedValue = Integer.parseInt(value);
            return parsedValue > 0 ? parsedValue : fallback;
        } catch (NumberFormatException | NullPointerException e) {
            return fallback;
        }
    }
}
