package com.tcg.bot.e2e.soporte;

import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;

/**
 * Hooks de Cucumber: preparan la corrida y dejan todo limpio entre escenarios (CA2 de HU-008).
 * <ul>
 *   <li>Al arrancar: foto del Sheet de QA, app levantada y navegador abierto.</li>
 *   <li>Antes de cada escenario: configuración de la app en sus valores por defecto (Sheet de QA, pestaña "Inventario").</li>
 *   <li>Después de cada escenario, pase o falle: evidencias si falló y el Sheet de QA vuelve a la foto.</li>
 * </ul>
 */
public class GanchosE2E {

    private static final long DURACION_MINIMA_MS = 15_000L;

    private final NavegadorE2E navegador;
    private long inicioEscenario;

    /**
     * Cucumber lo crea en cada escenario con el navegador de ese escenario.
     * Ejemplo: {@code new GanchosE2E(navegador)}.
     */
    public GanchosE2E(NavegadorE2E navegador) {
        this.navegador = navegador;
    }

    /**
     * Prepara la corrida: foto del Sheet de QA (si falta la clave, frena acá), app y navegador.
     * Ejemplo: primera vez en la corrida → la app queda en {@code http://127.0.0.1:<puerto libre>}.
     */
    @BeforeAll
    public static void prepararCorrida() {
        SheetDeQa.guardarEstadoInicial();
        AppParaE2E.arrancar();
        NavegadorE2E.abrir();
    }

    /**
     * Cierra el navegador y apaga la app. Ejemplo: al terminar la corrida el puerto queda libre.
     */
    @AfterAll
    public static void cerrarCorrida() {
        try {
            NavegadorE2E.cerrar();
        } finally {
            AppParaE2E.detener();
        }
    }

    /**
     * Cada escenario arranca con la configuración por defecto, sin depender del anterior.
     * Ejemplo: el escenario anterior guardó la tienda "Codex Live" → este ve "Inventory Manager".
     */
    @Before
    public void prepararEscenario() {
        AppParaE2E.borrarConfiguracionGuardada();
        inicioEscenario = System.nanoTime();
    }

    /**
     * Google deja 60 lecturas por minuto por proyecto y cada escenario gasta unas 10 (la app y la restauración del Sheet).
     * Para no pasarse, un escenario corto espera hasta completar {@value #DURACION_MINIMA_MS} ms antes de seguir.
     * Ejemplo: escenario de 4 s → espera 11 s.
     */
    private void respetarCupoDeGoogle() {
        long faltanteMs = DURACION_MINIMA_MS - (System.nanoTime() - inicioEscenario) / 1_000_000L;
        if (faltanteMs > 0) {
            try {
                Thread.sleep(faltanteMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Guarda evidencias si falló, cierra la pestaña y deja el Sheet de QA como estaba al arrancar la corrida.
     * Ejemplo: el escenario creó la pestaña "Caja" → después de este hook "Caja" ya no está.
     */
    @After
    public void limpiarEscenario(Scenario escenario) {
        try {
            navegador.terminarEscenario(escenario);
        } finally {
            try {
                SheetDeQa.volverAlEstadoInicial();
            } finally {
                respetarCupoDeGoogle();
            }
        }
    }
}
