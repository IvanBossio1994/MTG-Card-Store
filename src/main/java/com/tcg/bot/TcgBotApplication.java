package com.tcg.bot;

import com.tcg.bot.desktop.DesktopLauncher;
import com.tcg.bot.update.UpdateInstaller;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TcgBotApplication {

    public static void main(String[] args) {
        // Version de escritorio: si la app ya esta abierta, un segundo doble clic solo abre el navegador.
        if (DesktopLauncher.isDesktop() && DesktopLauncher.alreadyRunning()) {
            DesktopLauncher.openBrowser();
            return;
        }

        // Si quedo una version nueva descargada, se instala ahora y la app se vuelve a abrir sola.
        if (DesktopLauncher.isDesktop() && !java.util.Arrays.asList(args).contains(UpdateInstaller.RELAUNCHED_ARG)
                && UpdateInstaller.applyPendingAtStartup(System.getProperty("app.version", "dev"))) {
            return;
        }

        SpringApplication.run(TcgBotApplication.class, args);
    }
}
