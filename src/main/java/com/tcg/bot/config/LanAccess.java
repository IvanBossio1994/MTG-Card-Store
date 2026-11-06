package com.tcg.bot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Segunda caja en el celular: con esto prendido, la app también escucha en la red local (en otro puerto) para que
 * un celular conectado al mismo Wi-Fi pueda buscar, vender y anotar reservas. Para entrar hace falta el PIN que se
 * ve en la PC. Lo que se puede hacer desde el celular lo decide {@link LocalOnlyFilter}.
 */
@Component
public class LanAccess {

    public static final String COOKIE = "tcg-caja";
    public static final int DEFAULT_PORT = 8090;
    /** Intentos de PIN equivocados seguidos antes de bloquear un rato. */
    static final int MAX_FAILURES = 5;
    static final long LOCK_SECONDS = 60;

    private static final Logger log = LoggerFactory.getLogger(LanAccess.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path settingsFile;
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();
    Clock clock = Clock.systemUTC();

    private volatile boolean enabled;
    private volatile int port = DEFAULT_PORT;
    private volatile String pin = "";
    private int failures;
    private Instant lockedUntil = Instant.EPOCH;

    public LanAccess(@Value("${app.storage-dir:./data}") String storageDirectory) {
        this.settingsFile = Paths.get(storageDirectory).resolve("lan.properties");
        load();
        if (pin.isBlank()) {
            pin = newPin();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPort() {
        return port;
    }

    public String getPin() {
        return pin;
    }

    public synchronized void setEnabled(boolean value) throws IOException {
        enabled = value;
        if (!value) {
            sessions.clear();
        }
        save();
    }

    /** PIN nuevo: los celulares que ya habían entrado tienen que volver a ponerlo. */
    public synchronized void changePin() throws IOException {
        pin = newPin();
        sessions.clear();
        save();
    }

    /** Devuelve una sesión nueva si el PIN es correcto, o null. Después de varios errores se bloquea un minuto. */
    public synchronized String login(String attempt) {
        Instant now = clock.instant();
        if (now.isBefore(lockedUntil)) {
            return null;
        }
        if (attempt != null && java.security.MessageDigest.isEqual(attempt.trim().getBytes(), pin.getBytes())) {
            failures = 0;
            byte[] bytes = new byte[24];
            RANDOM.nextBytes(bytes);
            String session = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            sessions.add(session);
            return session;
        }
        failures++;
        if (failures >= MAX_FAILURES) {
            failures = 0;
            lockedUntil = now.plusSeconds(LOCK_SECONDS);
        }
        return null;
    }

    public synchronized boolean isLocked() {
        return clock.instant().isBefore(lockedUntil);
    }

    public boolean isValidSession(String session) {
        return session != null && sessions.contains(session);
    }

    /** Direcciones de esta PC en la red local, para armar el link que se abre en el celular. */
    public List<String> addresses() {
        List<String> found = new ArrayList<>();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && isPrivate(address.getHostAddress())) {
                        found.add(address.getHostAddress());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("No se pudieron leer las direcciones de red: {}", e.getMessage());
        }
        // Las de casa/oficina (192.168.x.x) primero: son las del Wi-Fi casi siempre.
        found.sort((a, b) -> Boolean.compare(!a.startsWith("192.168."), !b.startsWith("192.168.")));
        return found;
    }

    /** IPv4 de red privada (10.x, 172.16-31.x, 192.168.x). Solo se acepta entrar desde el celular con una de estas. */
    public static boolean isPrivate(String host) {
        if (host == null || !host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return false;
        }
        String[] parts = host.split("\\.");
        int a = Integer.parseInt(parts[0]);
        int b = Integer.parseInt(parts[1]);
        for (String part : parts) {
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168);
    }

    private static String newPin() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private void load() {
        if (!Files.exists(settingsFile)) {
            return;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(settingsFile)) {
            properties.load(input);
            enabled = Boolean.parseBoolean(properties.getProperty("enabled", "false"));
            pin = properties.getProperty("pin", "").trim();
            try {
                port = Integer.parseInt(properties.getProperty("port", String.valueOf(DEFAULT_PORT)).trim());
            } catch (NumberFormatException e) {
                port = DEFAULT_PORT;
            }
        } catch (IOException e) {
            enabled = false;
        }
    }

    private void save() throws IOException {
        Files.createDirectories(settingsFile.getParent());
        Properties properties = new Properties();
        properties.setProperty("enabled", String.valueOf(enabled));
        properties.setProperty("port", String.valueOf(port));
        properties.setProperty("pin", pin);
        try (OutputStream output = Files.newOutputStream(settingsFile)) {
            properties.store(output, "Segunda caja en el celular");
        }
    }
}
