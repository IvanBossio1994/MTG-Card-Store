package com.tcg.bot.desktop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;

/**
 * Comportamiento de la version instalada (perfil "desktop"): abre el navegador cuando la app termina de arrancar.
 */
@Component
@Profile("desktop")
public class DesktopLauncher {

    public static final String URL = "http://localhost:8080/";

    private static final Logger log = LoggerFactory.getLogger(DesktopLauncher.class);

    /** true cuando la app se reabrio sola despues de actualizarse: la pestana que ya estaba abierta se recarga sola. */
    @Value("${app.updates.relaunched:false}")
    private boolean relaunchedAfterUpdate;

    @Value("${app.version:dev}")
    private String version;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        System.out.println();
        System.out.println("TCG Store Helper " + version + " esta funcionando en " + URL);
        System.out.println("Para cerrar la app, cerra esta ventana.");
        if (!relaunchedAfterUpdate) {
            openBrowser();
        }
    }

    public static boolean isDesktop() {
        String profiles = System.getProperty("spring.profiles.active", "");
        return profiles.contains("desktop");
    }

    public static boolean alreadyRunning() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 8080), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void openBrowser() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] command;
        if (os.contains("win")) {
            command = new String[]{"rundll32", "url.dll,FileProtocolHandler", URL};
        } else if (os.contains("mac")) {
            command = new String[]{"open", URL};
        } else {
            command = new String[]{"xdg-open", URL};
        }

        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            log.info("Abrí {} en tu navegador.", URL);
        }
    }
}
