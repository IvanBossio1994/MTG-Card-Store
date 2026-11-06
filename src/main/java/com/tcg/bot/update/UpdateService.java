package com.tcg.bot.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Actualizaciones automaticas de la version de Windows.
 * <p>
 * Al abrir la app (y cada algunas horas) mira el ultimo release publicado en GitHub. Si hay una version mas nueva,
 * la descarga sola en segundo plano y queda lista: se instala con el boton "Actualizar ahora" o, si no, la proxima
 * vez que se abra la app. La configuracion, la licencia y las claves estan en la carpeta de datos y no se tocan.
 */
@Service
public class UpdateService {

    public static final String ASSET_NAME = "TCGStoreHelper-windows.zip";

    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);

    public enum State { DISABLED, IDLE, CHECKING, UP_TO_DATE, DOWNLOADING, READY, INSTALLING, ERROR }

    /** Lo que muestran las pantallas. */
    public record Status(State state, String currentVersion, String latestVersion, String releaseUrl) {
        public boolean ready() {
            return state == State.READY;
        }

        public boolean enabled() {
            return state != State.DISABLED;
        }
    }

    /** Ultimo release publicado. sha256Url puede faltar. */
    public record Release(String version, String htmlUrl, String zipUrl, String sha256Url) {
    }

    /** Se separa para poder probar sin internet. */
    public interface ReleaseSource {
        Release latest() throws Exception;

        void download(String url, Path target) throws Exception;

        String fetchText(String url) throws Exception;
    }

    /** Instala y cierra la app; se separa para las pruebas. */
    public interface Installer {
        void install(Path storageDir, UpdateInstaller.Pending pending) throws Exception;
    }

    private final Path storageDir;
    private final AppVersion currentVersion;
    private final String currentVersionText;
    private final boolean enabled;
    private final ReleaseSource source;
    private final Installer installer;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "actualizaciones");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private volatile State state;
    private volatile String latestVersion = "";
    private volatile String releaseUrl = "";

    @Autowired
    public UpdateService(
            Environment environment,
            @Value("${app.storage-dir:./data}") String storageDirectory,
            @Value("${app.version:dev}") String version,
            @Value("${app.updates.enabled:true}") boolean updatesEnabled,
            @Value("${app.updates.repository:IvanBossio1994/MTG-Card-Store}") String repository
    ) {
        this(Paths.get(storageDirectory), version,
                updatesEnabled && environment.acceptsProfiles(Profiles.of("desktop")) && UpdateInstaller.canInstall(),
                new GitHubReleaseSource(repository, version), UpdateService::installAndExit);
    }

    UpdateService(Path storageDir, String version, boolean enabled, ReleaseSource source, Installer installer) {
        this.storageDir = storageDir;
        this.currentVersionText = version == null || version.isBlank() ? "dev" : version.trim();
        this.currentVersion = AppVersion.parse(currentVersionText);
        this.enabled = enabled && currentVersion != null;
        this.source = source;
        this.installer = installer;
        this.state = this.enabled ? State.IDLE : State.DISABLED;
        if (this.enabled) {
            forgetInstalledUpdates();
        }
    }

    public Status status() {
        return new Status(state, currentVersionText, latestVersion, releaseUrl);
    }

    @Scheduled(initialDelay = 10_000, fixedDelay = 6 * 60 * 60_000)
    public void scheduledCheck() {
        checkInBackground();
    }

    /** Busca (y descarga) una version nueva sin frenar la pantalla. */
    public void checkInBackground() {
        if (!enabled || !busy.compareAndSet(false, true)) {
            return;
        }
        worker.submit(() -> {
            try {
                checkNow();
            } finally {
                busy.set(false);
            }
        });
    }

    /** Hace el chequeo en este mismo hilo. */
    void checkNow() {
        if (!enabled || state == State.INSTALLING) {
            return;
        }
        state = State.CHECKING;
        try {
            Release release = source.latest();
            AppVersion latest = release == null ? null : AppVersion.parse(release.version());
            if (latest == null || !latest.isNewerThan(currentVersion)) {
                state = State.UP_TO_DATE;
                return;
            }
            latestVersion = latest.toString();
            releaseUrl = release.htmlUrl() == null ? "" : release.htmlUrl();

            UpdateInstaller.Pending pending = UpdateInstaller.readPending(storageDir);
            if (pending != null && latest.equals(AppVersion.parse(pending.version()))
                    && UpdateInstaller.looksInstallable(pending.source())) {
                state = State.READY;
                return;
            }
            if (release.zipUrl() == null || release.zipUrl().isBlank()) {
                // El release todavia no tiene el .zip (se esta armando): se reintenta mas tarde.
                state = State.UP_TO_DATE;
                return;
            }

            state = State.DOWNLOADING;
            Path folder = download(release, latest);
            UpdateInstaller.writePending(storageDir, new UpdateInstaller.Pending(latest.toString(), folder, 0));
            state = State.READY;
            log.info("Version {} descargada; se instala al reiniciar la app.", latest);
        } catch (Exception e) {
            log.warn("No se pudo buscar o descargar la actualizacion: {}", e.getMessage());
            state = State.ERROR;
        }
    }

    /** Instala la version descargada: la app se cierra y se vuelve a abrir sola. */
    public boolean installNow() {
        if (state != State.READY) {
            return false;
        }
        UpdateInstaller.Pending pending = UpdateInstaller.readPending(storageDir);
        if (pending == null || !UpdateInstaller.looksInstallable(pending.source())) {
            state = State.ERROR;
            return false;
        }
        try {
            state = State.INSTALLING;
            installer.install(storageDir, pending);
            return true;
        } catch (Exception e) {
            log.warn("No se pudo instalar la actualizacion: {}", e.getMessage());
            state = State.ERROR;
            return false;
        }
    }

    private Path download(Release release, AppVersion version) throws Exception {
        Path folder = UpdateInstaller.updatesDir(storageDir).resolve(version.toString());
        UpdateInstaller.deleteRecursively(folder);
        Files.createDirectories(folder);
        Path zip = folder.resolve(ASSET_NAME);
        Path partial = folder.resolve(ASSET_NAME + ".part");
        source.download(release.zipUrl(), partial);

        if (release.sha256Url() != null && !release.sha256Url().isBlank()) {
            String expected = source.fetchText(release.sha256Url()).trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
            String actual = sha256(partial);
            if (!expected.equals(actual)) {
                UpdateInstaller.deleteRecursively(folder);
                throw new IOException("La descarga llego danada (no coincide el sha256)");
            }
        }
        Files.move(partial, zip, StandardCopyOption.REPLACE_EXISTING);

        Path extracted = folder.resolve("files");
        UpdateInstaller.unzip(zip, extracted);
        Files.deleteIfExists(zip);
        Path exe = UpdateInstaller.appExecutable();
        String exeName = exe == null ? "TCGStoreHelper.exe" : exe.getFileName().toString();
        Path appFolder = UpdateInstaller.findAppFolder(extracted, exeName);
        if (appFolder == null || !UpdateInstaller.looksInstallable(appFolder)) {
            UpdateInstaller.deleteRecursively(folder);
            throw new IOException("La descarga no trae la app completa");
        }
        return appFolder;
    }

    /** Si ya se instalo la version descargada (o una mas nueva), borra lo descargado. */
    private void forgetInstalledUpdates() {
        UpdateInstaller.Pending pending = UpdateInstaller.readPending(storageDir);
        if (pending == null) {
            return;
        }
        AppVersion target = AppVersion.parse(pending.version());
        if (target == null || !target.isNewerThan(currentVersion)) {
            UpdateInstaller.clearUpdates(storageDir);
        }
    }

    static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void installAndExit(Path storageDir, UpdateInstaller.Pending pending) throws Exception {
        UpdateInstaller.launch(storageDir, pending, true);
        // Se deja un momento para que la pantalla reciba la respuesta antes de cerrar.
        Thread closer = new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        }, "cerrar-para-actualizar");
        closer.setDaemon(false);
        closer.start();
    }

    /** Lee el ultimo release publico de GitHub (sin cuenta ni clave). */
    static final class GitHubReleaseSource implements ReleaseSource {

        private final String latestUrl;
        private final String userAgent;
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        GitHubReleaseSource(String repository, String version) {
            this.latestUrl = "https://api.github.com/repos/" + repository + "/releases/latest";
            this.userAgent = "TCGStoreHelper/" + version;
        }

        @Override
        public Release latest() throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(latestUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", userAgent)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) {
                return null; // Todavia no hay ningun release publicado.
            }
            if (response.statusCode() != 200) {
                throw new IOException("GitHub respondio " + response.statusCode());
            }
            return parseRelease(response.body());
        }

        @Override
        public void download(String url, Path target) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(15))
                    .header("User-Agent", userAgent)
                    .GET()
                    .build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(target);
                throw new IOException("La descarga respondio " + response.statusCode());
            }
        }

        @Override
        public String fetchText(String url) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", userAgent)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IOException("GitHub respondio " + response.statusCode());
            }
            return response.body();
        }
    }

    static Release parseRelease(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("draft") && root.get("draft").getAsBoolean()
                || root.has("prerelease") && root.get("prerelease").getAsBoolean()) {
            return null;
        }
        String tag = text(root, "tag_name");
        String htmlUrl = text(root, "html_url");
        String zipUrl = null;
        String shaUrl = null;
        JsonElement assets = root.get("assets");
        if (assets != null && assets.isJsonArray()) {
            for (JsonElement element : (JsonArray) assets) {
                JsonObject asset = element.getAsJsonObject();
                String name = text(asset, "name");
                if (ASSET_NAME.equals(name)) {
                    zipUrl = text(asset, "browser_download_url");
                } else if ((ASSET_NAME + ".sha256").equals(name)) {
                    shaUrl = text(asset, "browser_download_url");
                }
            }
        }
        return new Release(tag, htmlUrl, zipUrl, shaUrl);
    }

    private static String text(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
