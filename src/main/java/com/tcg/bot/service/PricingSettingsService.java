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

    private static final String PESOS_PER_POINT_KEY =
            "points.pesos-per-point";

    private final double defaultCkDollarRate;
    private final int defaultRoundMultiple;
    private final double defaultPesosPerPoint;
    private final Path settingsFile;

    private volatile double ckDollarRate;
    private volatile int roundMultiple;
    private volatile double pesosPerPoint;

    public PricingSettingsService(
            @Value("${pricing.ck-dollar-rate:1650.0}") double defaultCkDollarRate,
            @Value("${pricing.round-multiple:500}") int defaultRoundMultiple,
            @Value("${points.pesos-per-point:1.0}") double defaultPesosPerPoint,
            @Value("${app.storage-dir:D:/TCG-inventory/data}") String storageDirectory
    ) {
        this.defaultCkDollarRate = defaultCkDollarRate;
        this.defaultRoundMultiple = defaultRoundMultiple;
        this.defaultPesosPerPoint = defaultPesosPerPoint;
        this.settingsFile = Paths.get(storageDirectory).resolve("pricing.properties");
        this.ckDollarRate = defaultCkDollarRate;
        this.roundMultiple = defaultRoundMultiple;
        this.pesosPerPoint = defaultPesosPerPoint;
        load();
    }

    public double getCkDollarRate() {
        return ckDollarRate;
    }

    public int getRoundMultiple() {
        return roundMultiple;
    }

    public double getPesosPerPoint() {
        return pesosPerPoint;
    }

    public long pointsCostForPrice(double localTotal) {
        if (localTotal <= 0 || pesosPerPoint <= 0) {
            return 0;
        }

        return (long) Math.ceil(localTotal / pesosPerPoint);
    }

    public synchronized void update(double newCkDollarRate, int newRoundMultiple, double newPesosPerPoint) throws IOException {
        if (newCkDollarRate <= 0) {
            throw new IllegalArgumentException("La cotizacion debe ser mayor a cero.");
        }

        if (newRoundMultiple <= 0) {
            throw new IllegalArgumentException("El multiplo debe ser mayor a cero.");
        }

        if (newPesosPerPoint <= 0) {
            throw new IllegalArgumentException("Los pesos por punto deben ser mayores a cero.");
        }

        Properties properties = new Properties();
        properties.setProperty(DOLLAR_RATE_KEY, Double.toString(newCkDollarRate));
        properties.setProperty(ROUND_MULTIPLE_KEY, Integer.toString(newRoundMultiple));
        properties.setProperty(PESOS_PER_POINT_KEY, Double.toString(newPesosPerPoint));

        Files.createDirectories(settingsFile.getParent());

        try (OutputStream outputStream = Files.newOutputStream(settingsFile)) {
            properties.store(outputStream, "Regla de precio configurada desde la aplicacion");
        }

        ckDollarRate = newCkDollarRate;
        roundMultiple = newRoundMultiple;
        pesosPerPoint = newPesosPerPoint;
    }

    public synchronized void update(double newCkDollarRate, int newRoundMultiple) throws IOException {
        update(newCkDollarRate, newRoundMultiple, pesosPerPoint);
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
            pesosPerPoint = parsePositiveDouble(
                    properties.getProperty(PESOS_PER_POINT_KEY),
                    defaultPesosPerPoint
            );
        } catch (IOException e) {
            ckDollarRate = defaultCkDollarRate;
            roundMultiple = defaultRoundMultiple;
            pesosPerPoint = defaultPesosPerPoint;
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
