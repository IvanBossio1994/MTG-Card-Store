package com.tcg.bot;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.awt.Desktop;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class DesktopLauncher {

    private static final String APP_NAME = "TCG Inventory";

    public static void main(String[] args) {
        int port = freePort();
        Path dataDirectory = dataDirectory();

        System.setProperty("server.port", String.valueOf(port));
        System.setProperty("app.storage-dir", dataDirectory.toString());

        ConfigurableApplicationContext context = new SpringApplicationBuilder(TcgBotApplication.class)
                .headless(false)
                .run(args);

        Runtime.getRuntime().addShutdownHook(new Thread(context::close));
        openAppWindow("http://127.0.0.1:" + port + "/", dataDirectory, context);
    }

    private static void openAppWindow(String url, Path dataDirectory, ConfigurableApplicationContext context) {
        try {
            Browser browser = browser();
            if (browser == null) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }

            Path profileDirectory = dataDirectory.resolve(browser.profileDirectory());
            Files.createDirectories(profileDirectory);

            Process edgeProcess = new ProcessBuilder(browser.arguments(url, profileDirectory)).start();

            edgeProcess.onExit()
                    .orTimeout(7, java.util.concurrent.TimeUnit.DAYS)
                    .whenComplete((ignored, error) -> {
                        context.close();
                        System.exit(0);
                    });
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo abrir la ventana de " + APP_NAME + ".", e);
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo encontrar un puerto local libre.", e);
        }
    }

    private static Path dataDirectory() {
        String configured = System.getenv("TCG_INVENTORY_DATA_DIR");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath().normalize();
        }

        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, APP_NAME, "data").toAbsolutePath().normalize();
        }

        return Path.of(System.getProperty("user.home"), "." + APP_NAME.replace(" ", "-").toLowerCase(), "data")
                .toAbsolutePath()
                .normalize();
    }

    private static Browser browser() {
        return List.of(
                        new Browser("edge-profile", "--app=", "--user-data-dir=", paths(
                                "Microsoft\\Edge\\Application\\msedge.exe"
                        )),
                        new Browser("chrome-profile", "--app=", "--user-data-dir=", paths(
                                "Google\\Chrome\\Application\\chrome.exe"
                        )),
                        new Browser("firefox-profile", "--new-window", "-profile", paths(
                                "Mozilla Firefox\\firefox.exe"
                        ))
                )
                .stream()
                .filter(Browser::exists)
                .findFirst()
                .orElse(null);
    }

    private static List<Path> paths(String relativePath) {
        return List.of(
                Path.of(System.getenv("ProgramFiles") == null ? "" : System.getenv("ProgramFiles"), relativePath),
                Path.of(System.getenv("ProgramFiles(x86)") == null ? "" : System.getenv("ProgramFiles(x86)"), relativePath),
                Path.of(System.getenv("LocalAppData") == null ? "" : System.getenv("LocalAppData"), relativePath)
        );
    }

    private record Browser(String profileDirectory, String openArgument, String profileArgument, List<Path> paths) {

        boolean exists() {
            return executable() != null;
        }

        String executable() {
            return paths.stream()
                    .filter(Files::exists)
                    .map(Path::toString)
                    .findFirst()
                    .orElse(null);
        }

        List<String> arguments(String url, Path profileDirectory) {
            String executable = executable();
            if ("--new-window".equals(openArgument)) {
                return List.of(executable, openArgument, url, profileArgument, profileDirectory.toString());
            }

            return List.of(executable, openArgument + url, profileArgument + profileDirectory, "--no-first-run");
        }
    }
}
