package com.tcg.bot.e2e.soporte;

import com.tcg.bot.TcgBotApplication;
import com.tcg.bot.compartido.AccesoSheet;
import com.tcg.bot.configuracion.ConfiguracionArchivosRepository;
import com.tcg.bot.configuracion.ConfiguracionRepository;
import com.tcg.bot.google.AccesoSheetCuentaServicio;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

/**
 * Arranca la app para los E2E, una sola vez por corrida, en un puerto libre y dentro del mismo proceso de los tests.
 * Usa el perfil {@code e2e} (sin la v1 ni el login de Google, con la sesión de Gmail simulada) pero con dos piezas reales:
 * el acceso al Sheet con la cuenta de servicio, que apunta al Sheet de QA, y la configuración en archivos dentro de
 * {@code target/e2e-datos} (nunca en {@code data/}). Así los escenarios escriben en el Sheet de QA sin login de Gmail.
 */
public final class AppParaE2E {

    /** Carpeta donde la app guarda la configuración durante los E2E. Se vacía antes de cada escenario. */
    static final Path CARPETA_DATOS = Paths.get("target", "e2e-datos");

    private static ConfigurableApplicationContext app;
    private static String urlBase;

    private AppParaE2E() {}

    /**
     * Levanta la app si todavía no está levantada y guarda su dirección.
     * Ejemplo: {@code arrancar()} → {@link #urlBase()} devuelve {@code "http://127.0.0.1:53817"}.
     */
    static synchronized void arrancar() {
        if (app != null) {
            return;
        }
        borrarConfiguracionGuardada();
        app = new SpringApplicationBuilder(TcgBotApplication.class)
                .profiles("e2e")
                .initializers(contexto -> usarSheetDeQa((GenericApplicationContext) contexto))
                // Como argumentos (no como propiedades por defecto) para que pisen a application.properties.
                .run("--server.port=0", "--app.storage-dir=" + CARPETA_DATOS, "--spring.main.banner-mode=off");
        int puerto = ((WebServerApplicationContext) app).getWebServer().getPort();
        urlBase = "http://127.0.0.1:" + puerto;
    }

    /**
     * Apaga la app al final de la corrida. Si no estaba levantada, no hace nada.
     * Ejemplo: {@code detener()} → el puerto queda libre.
     */
    static synchronized void detener() {
        if (app != null) {
            app.close();
            app = null;
            urlBase = null;
        }
    }

    /**
     * Dirección de la app levantada, sin barra final.
     * Ejemplo: {@code urlBase() + "/app/configuracion"} → {@code "http://127.0.0.1:53817/app/configuracion"}.
     */
    public static String urlBase() {
        if (urlBase == null) {
            throw new IllegalStateException("La app de los E2E no está levantada (la arranca GanchosE2E).");
        }
        return urlBase;
    }

    /**
     * Borra la configuración guardada (tienda, Sheet, precios, puntos y logo): la app vuelve a sus valores por defecto,
     * con el Sheet de QA y la pestaña "Inventario". Ejemplo: después de guardar la tienda "Codex Live", el escenario
     * siguiente vuelve a ver "Inventory Manager".
     */
    static void borrarConfiguracionGuardada() {
        if (!Files.exists(CARPETA_DATOS)) {
            return;
        }
        try (Stream<Path> archivos = Files.walk(CARPETA_DATOS)) {
            for (Path archivo : archivos.sorted(Comparator.reverseOrder()).toList()) {
                if (!archivo.equals(CARPETA_DATOS)) {
                    Files.delete(archivo);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo vaciar " + CARPETA_DATOS, e);
        }
    }

    /**
     * Reemplaza, solo en esta app, el Sheet simulado del modo prueba por el Sheet de QA real (cuenta de servicio) y la
     * configuración en memoria por la de archivos en {@link #CARPETA_DATOS}, con el Sheet de QA como Sheet por defecto.
     */
    private static void usarSheetDeQa(GenericApplicationContext contexto) {
        String clave = SheetDeQa.ARCHIVO_CLAVE.toString();
        contexto.registerBean(
                "accesoSheetDeQa",
                AccesoSheet.class,
                () -> new AccesoSheetCuentaServicio(clave),
                definicion -> definicion.setPrimary(true));
        contexto.registerBean(
                "configuracionDeQa",
                ConfiguracionRepository.class,
                () -> new ConfiguracionArchivosRepository(CARPETA_DATOS.toString(), SheetDeQa.ID, 1650, 500, 1),
                definicion -> definicion.setPrimary(true));
    }
}
