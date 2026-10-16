package com.tcg.bot.e2e.compartido;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.tcg.bot.e2e.localizadores.AppLocalizadores;
import com.tcg.bot.e2e.soporte.NavegadorE2E;
import java.text.Normalizer;
import java.util.Arrays;

/**
 * El marco de todas las pantallas v2 (espeja {@code frontend/src/app/MarcoApp.tsx}): el menú lateral con los módulos.
 * Ejemplo: {@code marco.irA(Modulo.deNombreVisible("Configuración"))} → hace clic en "Configuración" del menú.
 */
public class MarcoApp {

    /** Módulos del menú, con el texto que se ve y su enlace. */
    public enum Modulo {
        INVENTARIO("Inventario", AppLocalizadores.ENLACE_INVENTARIO),
        RESERVAS("Reservas", AppLocalizadores.ENLACE_RESERVAS),
        CAJA("Caja", AppLocalizadores.ENLACE_CAJA),
        MOVIMIENTOS("Movimientos", AppLocalizadores.ENLACE_MOVIMIENTOS),
        CONFIGURACION("Configuración", AppLocalizadores.ENLACE_CONFIGURACION);

        private final String nombreVisible;
        private final String enlace;

        Modulo(String nombreVisible, String enlace) {
            this.nombreVisible = nombreVisible;
            this.enlace = enlace;
        }

        /**
         * Busca el módulo por el texto del menú (sin importar tildes ni mayúsculas).
         * Ejemplo: {@code deNombreVisible("Configuración")} → {@code CONFIGURACION}; "Ventas" → error con la lista válida.
         */
        public static Modulo deNombreVisible(String nombre) {
            return Arrays.stream(values())
                    .filter(modulo -> sinTildes(modulo.nombreVisible).equals(sinTildes(nombre)))
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalArgumentException("No hay un módulo \"" + nombre + "\" en el menú. Válidos: "
                                    + Arrays.stream(values())
                                            .map(modulo -> modulo.nombreVisible)
                                            .toList()));
        }

        /** Ruta del módulo en la app. Ejemplo: {@code CONFIGURACION.ruta()} → {@code "/app/configuracion"}. */
        public String ruta() {
            return "/app/" + name().toLowerCase();
        }

        /** Texto del menú. Ejemplo: {@code CONFIGURACION.nombreVisible()} → {@code "Configuración"}. */
        public String nombreVisible() {
            return nombreVisible;
        }

        private static String sinTildes(String texto) {
            return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase();
        }
    }

    private final NavegadorE2E navegador;

    /**
     * Crea el marco sobre el navegador del escenario. Ejemplo: {@code new MarcoApp(navegador)}.
     */
    public MarcoApp(NavegadorE2E navegador) {
        this.navegador = navegador;
    }

    /**
     * Hace clic en el módulo del menú lateral. Ejemplo: {@code irA(Modulo.CONFIGURACION)} → navega a /app/configuracion.
     */
    public void irA(Modulo modulo) {
        enlace(modulo).click();
    }

    /**
     * Verifica (con espera automática) que el módulo esté marcado como actual en el menú ({@code aria-current="page"}).
     * Ejemplo: en /app/configuracion → {@code esperarModuloActivo(CONFIGURACION)} pasa; con INVENTARIO falla.
     */
    public void esperarModuloActivo(Modulo modulo) {
        assertThat(enlace(modulo)).hasAttribute("aria-current", "page");
    }

    private Locator enlace(Modulo modulo) {
        return navegador.pagina().getByTestId(modulo.enlace);
    }
}
