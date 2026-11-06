package com.tcg.bot.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpdateServiceTest {

    @TempDir
    Path temp;

    /** GitHub de mentira: devuelve un release y sirve el .zip desde disco. */
    static final class FakeSource implements UpdateService.ReleaseSource {
        UpdateService.Release release;
        Path zip;
        String sha = "";
        int downloads;

        @Override
        public UpdateService.Release latest() {
            return release;
        }

        @Override
        public void download(String url, Path target) throws IOException {
            downloads++;
            Files.copy(zip, target);
        }

        @Override
        public String fetchText(String url) {
            return sha;
        }
    }

    private Path appZip(String... extraEntries) throws IOException {
        Path zip = temp.resolve("release.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            List<String> entries = new ArrayList<>(List.of("TCGStoreHelper/TCGStoreHelper.exe", "TCGStoreHelper/app/app.jar",
                    "TCGStoreHelper/runtime/bin/java.dll"));
            entries.addAll(List.of(extraEntries));
            for (String name : entries) {
                out.putNextEntry(new ZipEntry(name));
                out.write(name.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip;
    }

    private FakeSource source(String version, boolean withSha) throws IOException {
        FakeSource source = new FakeSource();
        source.zip = appZip();
        source.release = new UpdateService.Release("v" + version, "https://github.com/x/y/releases/tag/v" + version,
                "https://github.com/x/y/releases/download/v" + version + "/TCGStoreHelper-windows.zip",
                withSha ? "https://github.com/x/y/releases/download/v" + version + "/TCGStoreHelper-windows.zip.sha256" : null);
        return source;
    }

    @Test
    void downloadsNewerVersionAndLeavesItReady() throws Exception {
        Path data = temp.resolve("data");
        FakeSource source = source("1.1.0", true);
        source.sha = UpdateService.sha256(source.zip) + "  TCGStoreHelper-windows.zip\n";
        UpdateService service = new UpdateService(data, "1.0.0", true, source, (dir, pending) -> { });

        service.checkNow();

        assertThat(service.status().state()).isEqualTo(UpdateService.State.READY);
        assertThat(service.status().latestVersion()).isEqualTo("1.1.0");
        UpdateInstaller.Pending pending = UpdateInstaller.readPending(data);
        assertThat(pending.version()).isEqualTo("1.1.0");
        assertThat(pending.source().resolve("TCGStoreHelper.exe")).exists();
        assertThat(pending.source().resolve("app").resolve("app.jar")).exists();

        // Un segundo chequeo no vuelve a descargar.
        service.checkNow();
        assertThat(source.downloads).isEqualTo(1);
    }

    @Test
    void sameOrOlderVersionIsUpToDate() throws Exception {
        FakeSource source = source("1.0.0", false);
        UpdateService service = new UpdateService(temp.resolve("data"), "1.0.0", true, source, (dir, pending) -> { });

        service.checkNow();

        assertThat(service.status().state()).isEqualTo(UpdateService.State.UP_TO_DATE);
        assertThat(source.downloads).isZero();
    }

    @Test
    void damagedDownloadIsDiscarded() throws Exception {
        Path data = temp.resolve("data");
        FakeSource source = source("1.1.0", true);
        source.sha = "0000";
        UpdateService service = new UpdateService(data, "1.0.0", true, source, (dir, pending) -> { });

        service.checkNow();

        assertThat(service.status().state()).isEqualTo(UpdateService.State.ERROR);
        assertThat(UpdateInstaller.readPending(data)).isNull();
    }

    @Test
    void devBuildsAndNonDesktopDoNotUpdate() throws Exception {
        FakeSource source = source("9.0.0", false);
        UpdateService dev = new UpdateService(temp.resolve("a"), "dev", true, source, (dir, pending) -> { });
        UpdateService web = new UpdateService(temp.resolve("b"), "1.0.0", false, source, (dir, pending) -> { });

        dev.checkNow();
        web.checkNow();

        assertThat(dev.status().state()).isEqualTo(UpdateService.State.DISABLED);
        assertThat(web.status().state()).isEqualTo(UpdateService.State.DISABLED);
        assertThat(source.downloads).isZero();
    }

    @Test
    void installNowRunsTheInstallerWithTheDownloadedFolder() throws Exception {
        Path data = temp.resolve("data");
        List<UpdateInstaller.Pending> installed = new ArrayList<>();
        UpdateService service = new UpdateService(data, "1.0.0", true, source("1.2.0", false),
                (dir, pending) -> installed.add(pending));

        assertThat(service.installNow()).isFalse();
        service.checkNow();
        assertThat(service.installNow()).isTrue();

        assertThat(installed).hasSize(1);
        assertThat(installed.get(0).version()).isEqualTo("1.2.0");
        assertThat(service.status().state()).isEqualTo(UpdateService.State.INSTALLING);
    }

    @Test
    void forgetsDownloadsOnceTheNewVersionIsInstalled() throws Exception {
        Path data = temp.resolve("data");
        new UpdateService(data, "1.0.0", true, source("1.1.0", false), (dir, pending) -> { }).checkNow();
        assertThat(UpdateInstaller.readPending(data)).isNotNull();

        new UpdateService(data, "1.1.0", true, source("1.1.0", false), (dir, pending) -> { });

        assertThat(UpdateInstaller.updatesDir(data)).doesNotExist();
    }

    @Test
    void unzipRejectsFilesOutsideTheFolder() throws Exception {
        Path zip = temp.resolve("evil.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("../../evil.txt"));
            out.write(1);
            out.closeEntry();
        }

        assertThatThrownBy(() -> UpdateInstaller.unzip(zip, temp.resolve("out")))
                .isInstanceOf(IOException.class);
        assertThat(temp.getParent().resolve("evil.txt")).doesNotExist();
    }

    @Test
    void readsGitHubReleaseJson() {
        String json = """
                {"tag_name":"v1.3.0","html_url":"https://github.com/IvanBossio1994/MTG-Card-Store/releases/tag/v1.3.0",
                 "draft":false,"prerelease":false,
                 "assets":[{"name":"TCGStoreHelper-windows.zip","browser_download_url":"https://example.test/a.zip"},
                           {"name":"TCGStoreHelper-windows.zip.sha256","browser_download_url":"https://example.test/a.sha256"}]}
                """;

        UpdateService.Release release = UpdateService.parseRelease(json);

        assertThat(release.version()).isEqualTo("v1.3.0");
        assertThat(release.zipUrl()).isEqualTo("https://example.test/a.zip");
        assertThat(release.sha256Url()).isEqualTo("https://example.test/a.sha256");
        assertThat(UpdateService.parseRelease("{\"tag_name\":\"v2.0.0\",\"prerelease\":true}")).isNull();
    }

}
