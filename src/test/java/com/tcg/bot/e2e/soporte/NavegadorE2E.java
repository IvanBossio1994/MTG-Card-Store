package com.tcg.bot.e2e.soporte;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import io.cucumber.java.Scenario;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Navegador de los E2E (Playwright). El navegador se abre una vez por corrida; cada escenario tiene su propia pestaña
 * limpia (sin cookies ni datos del anterior). Cucumber crea una instancia por escenario y la inyecta en los pasos y
 * las pantallas. Si el escenario falla, deja captura y traza en {@code target/evidencias/}.
 * <p>
 * Propiedades: {@code -De2e.navegador=chrome} (por defecto: el Chrome instalado, sin descargas) o {@code chromium}
 * (lo descarga Playwright la primera vez); {@code -De2e.ver-navegador=true} para verlo en pantalla.
 */
public class NavegadorE2E {

    /** Carpeta de capturas, trazas y reporte. No se versiona (está dentro de target). */
    static final Path CARPETA_EVIDENCIAS = Paths.get("target", "evidencias");

    private static final int ANCHO = 1440;
    private static final int ALTO = 900;
    private static final double ESPERA_MAXIMA_MS = 20_000;

    private static Playwright playwright;
    private static Browser navegador;

    private BrowserContext contexto;
    private Page pagina;

    /**
     * Abre el navegador (una sola vez por corrida). Con Chrome no descarga nada; con Chromium, Playwright lo baja a
     * {@code %LOCALAPPDATA%\ms-playwright} la primera vez.
     * Ejemplo: {@code abrir()} con {@code -De2e.navegador=chrome} → usa el Chrome instalado, sin ventana.
     */
    static synchronized void abrir() {
        if (navegador != null) {
            return;
        }
        boolean usarChromeInstalado = !"chromium".equalsIgnoreCase(System.getProperty("e2e.navegador", "chrome"));
        var opciones = new Playwright.CreateOptions();
        if (usarChromeInstalado) {
            opciones.setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));
        }
        playwright = Playwright.create(opciones);
        var lanzamiento = new BrowserType.LaunchOptions().setHeadless(!Boolean.getBoolean("e2e.ver-navegador"));
        if (usarChromeInstalado) {
            lanzamiento.setChannel("chrome");
        }
        navegador = playwright.chromium().launch(lanzamiento);
    }

    /**
     * Cierra el navegador al final de la corrida. Ejemplo: {@code cerrar()} → no queda ningún Chrome de los E2E abierto.
     */
    static synchronized void cerrar() {
        if (playwright != null) {
            playwright.close();
            playwright = null;
            navegador = null;
        }
    }

    /**
     * Pestaña del escenario actual; se crea la primera vez que se pide, apuntando a la app de los E2E.
     * Ejemplo: {@code pagina().navigate("/app/configuracion")} → abre Configuración en la app levantada.
     */
    public Page pagina() {
        if (pagina == null) {
            if (navegador == null) {
                throw new IllegalStateException("El navegador de los E2E no está abierto (lo abre GanchosE2E).");
            }
            contexto = navegador.newContext(new Browser.NewContextOptions()
                    .setBaseURL(AppParaE2E.urlBase())
                    .setViewportSize(ANCHO, ALTO)
                    .setLocale("es-AR"));
            contexto.setDefaultTimeout(ESPERA_MAXIMA_MS);
            contexto.tracing()
                    .start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
            pagina = contexto.newPage();
        }
        return pagina;
    }

    /**
     * Cierra la pestaña del escenario. Si falló, guarda la captura (también adjunta al reporte) y la traza de Playwright.
     * Ejemplo: falla "[Configuracion] Abre la pantalla" → {@code target/evidencias/Configuracion-Abre-la-pantalla.png} y {@code ...-traza.zip}.
     */
    void terminarEscenario(Scenario escenario) {
        if (contexto == null) {
            return;
        }
        try {
            if (escenario.isFailed()) {
                String nombre = nombreDeArchivo(escenario.getName());
                byte[] captura = pagina.screenshot(new Page.ScreenshotOptions()
                        .setPath(CARPETA_EVIDENCIAS.resolve(nombre + ".png"))
                        .setFullPage(true));
                escenario.attach(captura, "image/png", nombre);
                contexto.tracing()
                        .stop(new Tracing.StopOptions().setPath(CARPETA_EVIDENCIAS.resolve(nombre + "-traza.zip")));
            } else {
                contexto.tracing().stop();
            }
        } finally {
            contexto.close();
            contexto = null;
            pagina = null;
        }
    }

    /** "[Configuracion] Abre la pantalla" → "Configuracion-Abre-la-pantalla". */
    private static String nombreDeArchivo(String nombreEscenario) {
        return nombreEscenario.replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("(^-|-$)", "");
    }
}
