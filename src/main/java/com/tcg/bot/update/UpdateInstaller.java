package com.tcg.bot.update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Instala una actualizacion ya descargada en la version de Windows.
 * <p>
 * Mientras la app esta abierta Windows no deja reemplazar sus archivos, asi que se deja corriendo un script chico
 * de PowerShell que espera a que la app se cierre, copia la version nueva encima de la carpeta del programa y la
 * vuelve a abrir. Los datos del usuario (configuracion, licencia, claves) viven en otra carpeta y no se tocan.
 * <p>
 * No depende de Spring: tambien se usa al arrancar, antes de levantar la app.
 */
public final class UpdateInstaller {

    public static final String RELAUNCHED_ARG = "--app.updates.relaunched=true";
    static final int MAX_STARTUP_ATTEMPTS = 2;

    private static final Logger log = LoggerFactory.getLogger(UpdateInstaller.class);
    private static final String PENDING_FILE = "pending.properties";
    private static final String SCRIPT_FILE = "apply-update.ps1";

    /** Actualizacion descargada y lista para instalar. */
    public record Pending(String version, Path source, int attempts) {
    }

    private UpdateInstaller() {
    }

    public static Path updatesDir(Path storageDir) {
        return storageDir.resolve("updates");
    }

    /** La misma carpeta de datos que usa application-desktop.properties, para poder leerla sin Spring. */
    public static Path desktopStorageDir() {
        String custom = System.getenv("TCG_INVENTORY_DATA_DIR");
        if (custom != null && !custom.isBlank()) {
            return Paths.get(custom);
        }
        return Paths.get(System.getProperty("user.home"), "TCGStoreHelper", "data");
    }

    /** Ejecutable de la app (TCGStoreHelper.exe), o null si no se esta corriendo desde la version empaquetada. */
    public static Path appExecutable() {
        String path = System.getProperty("jpackage.app-path", "");
        return path.isBlank() ? null : Paths.get(path);
    }

    /** Solo se instala solo en Windows y desde el .exe empaquetado (no al correr el proyecto con Maven). */
    public static boolean canInstall() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path exe = appExecutable();
        return os.contains("win") && exe != null && exe.getParent() != null;
    }

    public static Pending readPending(Path storageDir) {
        Path file = updatesDir(storageDir).resolve(PENDING_FILE);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException e) {
            return null;
        }
        String version = properties.getProperty("version", "");
        String source = properties.getProperty("source", "");
        if (version.isBlank() || source.isBlank()) {
            return null;
        }
        int attempts;
        try {
            attempts = Integer.parseInt(properties.getProperty("attempts", "0"));
        } catch (NumberFormatException e) {
            attempts = 0;
        }
        return new Pending(version, Paths.get(source), attempts);
    }

    public static void writePending(Path storageDir, Pending pending) throws IOException {
        Path dir = updatesDir(storageDir);
        Files.createDirectories(dir);
        Properties properties = new Properties();
        properties.setProperty("version", pending.version());
        properties.setProperty("source", pending.source().toAbsolutePath().toString());
        properties.setProperty("attempts", Integer.toString(pending.attempts()));
        Path temp = dir.resolve(PENDING_FILE + ".tmp");
        try (OutputStream output = Files.newOutputStream(temp)) {
            properties.store(output, "Actualizacion descargada, se instala al reiniciar la app");
        }
        Files.move(temp, dir.resolve(PENDING_FILE), StandardCopyOption.REPLACE_EXISTING);
    }

    /** Borra descargas viejas (por ejemplo, despues de instalar una version nueva). */
    public static void clearUpdates(Path storageDir) {
        deleteRecursively(updatesDir(storageDir));
    }

    /**
     * Al abrir la app: si quedo una actualizacion descargada, la instala y reabre la app.
     *
     * @return true si se lanzo la instalacion (la app tiene que terminar sin arrancar).
     */
    public static boolean applyPendingAtStartup(String currentVersionText) {
        if (!canInstall()) {
            return false;
        }
        AppVersion current = AppVersion.parse(currentVersionText);
        if (current == null) {
            return false;
        }
        Path storageDir = desktopStorageDir();
        Pending pending = readPending(storageDir);
        if (pending == null) {
            return false;
        }
        AppVersion target = AppVersion.parse(pending.version());
        if (target == null || !target.isNewerThan(current)) {
            clearUpdates(storageDir);
            return false;
        }
        if (pending.attempts() >= MAX_STARTUP_ATTEMPTS || !looksInstallable(pending.source())) {
            return false;
        }
        try {
            writePending(storageDir, new Pending(pending.version(), pending.source(), pending.attempts() + 1));
            System.out.println("Instalando la version " + pending.version() + " de TCG Store Helper...");
            launch(storageDir, pending, false);
            return true;
        } catch (IOException e) {
            log.warn("No se pudo instalar la actualizacion al abrir: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Deja corriendo el script que instala la actualizacion cuando esta app se cierre.
     * Quien llama tiene que cerrar la app despues.
     */
    public static void launch(Path storageDir, Pending pending, boolean relaunched) throws IOException {
        Path exe = appExecutable();
        if (exe == null || exe.getParent() == null) {
            throw new IOException("La app no esta corriendo desde TCGStoreHelper.exe");
        }
        Path dir = updatesDir(storageDir);
        Files.createDirectories(dir);
        Path script = dir.resolve(SCRIPT_FILE);
        try (InputStream input = UpdateInstaller.class.getResourceAsStream("/update/" + SCRIPT_FILE)) {
            if (input == null) {
                throw new IOException("Falta el script de actualizacion");
            }
            Files.copy(input, script, StandardCopyOption.REPLACE_EXISTING);
        }

        List<String> command = new ArrayList<>(List.of(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
                "-File", script.toString(),
                "-ParentPid", Long.toString(ProcessHandle.current().pid()),
                "-Source", pending.source().toAbsolutePath().toString(),
                "-Target", exe.getParent().toString(),
                "-Exe", exe.toString(),
                "-Pending", dir.resolve(PENDING_FILE).toString(),
                "-Log", dir.resolve("update.log").toString()));
        if (relaunched) {
            command.add("-NoBrowser");
        }
        new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    /** La carpeta descargada tiene el .exe y el programa adentro. */
    static boolean looksInstallable(Path source) {
        Path exe = appExecutable();
        String exeName = exe == null ? "TCGStoreHelper.exe" : exe.getFileName().toString();
        return Files.isRegularFile(source.resolve(exeName)) && Files.isRegularFile(source.resolve("app").resolve("app.jar"));
    }

    /** Descomprime el .zip del release cuidando que ningun archivo quede fuera de la carpeta destino. */
    static void unzip(Path zip, Path target) throws IOException {
        Path root = target.toAbsolutePath().normalize();
        Files.createDirectories(root);
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                Path destination = root.resolve(name).normalize();
                if (destination.equals(root)) {
                    continue;
                }
                if (!destination.startsWith(root)) {
                    throw new IOException("Archivo fuera de lugar en la actualizacion: " + entry.getName());
                }
                if (entry.isDirectory() || name.endsWith("/")) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Busca, dentro de lo descomprimido, la carpeta que tiene el .exe (el .zip trae una carpeta TCGStoreHelper). */
    static Path findAppFolder(Path extracted, String exeName) throws IOException {
        if (Files.isRegularFile(extracted.resolve(exeName))) {
            return extracted;
        }
        try (Stream<Path> children = Files.list(extracted)) {
            for (Path child : children.sorted().toList()) {
                if (Files.isDirectory(child) && Files.isRegularFile(child.resolve(exeName))) {
                    return child;
                }
            }
        }
        return null;
    }

    static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path item : walk.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                    // Si algo queda bloqueado se borra la proxima vez.
                }
            }
        } catch (IOException ignored) {
            // Nada que borrar.
        }
    }
}
