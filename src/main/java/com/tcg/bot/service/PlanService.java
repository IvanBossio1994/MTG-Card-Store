package com.tcg.bot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Properties;

/**
 * Plan gratuito vs premium.
 * Gratis: todo habilitado hasta FREE_SALES_LIMIT ventas; despues, modo reducido.
 * Los registros (Movimientos/Caja) se escriben siempre, en cualquier plan.
 */
@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    public static final int FREE_SALES_LIMIT = 100;

    private static final String SALES_KEY = "sales.recorded";
    private static final String LAST_MANUAL_SYNC_KEY = "sync.manual.last-date";
    private static final long CASH_SHEET_RETRY_MILLIS = 5 * 60 * 1000;

    public enum Feature {
        NEW_RESERVATIONS,
        CLIENTS,
        IMPORT_LIST,
        MOVEMENT_HISTORY,
        AUTO_SYNC
    }

    private final GoogleSheetsService googleSheetsService;
    private final Path settingsFile;
    private final Clock clock;

    private int localSales;
    private Integer cashSheetSales;
    private long nextCashSheetAttemptMillis;
    private String lastManualSyncDate = "";
    // ---- Simulacion temporal del plan pago; se reemplaza por la licencia via Google OAuth ----
    private volatile boolean premium;

    @Autowired
    public PlanService(
            GoogleSheetsService googleSheetsService,
            @Value("${app.storage-dir:./data}") String storageDirectory
    ) {
        this(googleSheetsService, storageDirectory, Clock.system(ZoneId.of("America/Buenos_Aires")));
    }

    PlanService(GoogleSheetsService googleSheetsService, String storageDirectory, Clock clock) {
        this.googleSheetsService = googleSheetsService;
        this.settingsFile = Paths.get(storageDirectory).resolve("plan.properties");
        this.clock = clock;
        load();
    }

    public synchronized int recordedSales() {
        return Math.max(localSales, cashSheetSales());
    }

    public synchronized void recordSale() {
        localSales = recordedSales() + 1;
        if (cashSheetSales != null) {
            cashSheetSales++;
        }
        save();
    }

    public int salesLimit() {
        return FREE_SALES_LIMIT;
    }

    public int remainingFreeSales() {
        return Math.max(FREE_SALES_LIMIT - recordedSales(), 0);
    }

    public boolean isPremium() {
        return premium;
    }

    public boolean isLimited() {
        return !premium && recordedSales() >= FREE_SALES_LIMIT;
    }

    public boolean canUse(Feature feature) {
        return !isLimited();
    }

    public void activatePremiumSimulation() {
        premium = true;
    }

    /**
     * En modo reducido la sincronizacion manual se permite una vez por dia.
     */
    public synchronized boolean tryRegisterManualSync() {
        if (!isLimited()) {
            return true;
        }

        String today = LocalDate.now(clock).toString();
        if (today.equals(lastManualSyncDate)) {
            return false;
        }

        lastManualSyncDate = today;
        save();
        return true;
    }

    private int cashSheetSales() {
        if (cashSheetSales != null) {
            return cashSheetSales;
        }

        if (googleSheetsService == null
                || !googleSheetsService.hasOAuthToken()
                || clock.millis() < nextCashSheetAttemptMillis) {
            return 0;
        }

        try {
            cashSheetSales = googleSheetsService.getCashRegisterEntries().size();
            return cashSheetSales;
        } catch (Exception e) {
            nextCashSheetAttemptMillis = clock.millis() + CASH_SHEET_RETRY_MILLIS;
            log.warn("No se pudieron contar las ventas de Caja; se usa el contador local.", e);
            return 0;
        }
    }

    private void load() {
        if (!Files.exists(settingsFile)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(settingsFile)) {
            properties.load(inputStream);
            localSales = Math.max(parseInt(properties.getProperty(SALES_KEY)), 0);
            lastManualSyncDate = properties.getProperty(LAST_MANUAL_SYNC_KEY, "");
        } catch (IOException e) {
            log.warn("No se pudo leer la configuracion del plan.", e);
        }
    }

    private void save() {
        Properties properties = new Properties();
        properties.setProperty(SALES_KEY, Integer.toString(localSales));
        properties.setProperty(LAST_MANUAL_SYNC_KEY, lastManualSyncDate);

        try {
            Files.createDirectories(settingsFile.getParent());
            try (OutputStream outputStream = Files.newOutputStream(settingsFile)) {
                properties.store(outputStream, "Estado del plan");
            }
        } catch (IOException e) {
            log.warn("No se pudo guardar la configuracion del plan.", e);
        }
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
