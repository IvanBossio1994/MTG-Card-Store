package com.tcg.bot.e2e.pasos;

import com.tcg.bot.e2e.compartido.MarcoApp;
import com.tcg.bot.e2e.compartido.MarcoApp.Modulo;
import com.tcg.bot.e2e.compartido.PantallaBase;
import com.tcg.bot.e2e.paginas.ConfiguracionPantalla;
import com.tcg.bot.e2e.soporte.NavegadorE2E;
import io.cucumber.java.PendingException;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

/**
 * Pasos comunes a todos los módulos (pantalla {@code app}): abrir la app, moverse por el menú y ver un módulo.
 * Solo llaman al marco y a las pantallas; sin locators ni lógica.
 */
public class AppPasos {

    private final NavegadorE2E navegador;
    private final MarcoApp marco;
    private final ConfiguracionPantalla configuracion;

    /**
     * Cucumber lo crea por escenario e inyecta el navegador y las pantallas de ese escenario.
     * Ejemplo: {@code new AppPasos(navegador, new ConfiguracionPantalla(navegador))}.
     */
    public AppPasos(NavegadorE2E navegador, ConfiguracionPantalla configuracion) {
        this.navegador = navegador;
        this.marco = new MarcoApp(navegador);
        this.configuracion = configuracion;
    }

    /** Ejemplo: {@code Given abre la app} → abre /app en la app levantada para los E2E. */
    @Given("abre la app")
    public void abreLaApp() {
        navegador.pagina().navigate("/app");
    }

    /** Ejemplo: {@code Given abre el módulo de "Configuración"} → entra por su dirección y espera que cargue. */
    @Given("abre el módulo de {string}")
    public void abreElModuloDe(String nombreModulo) {
        pantallaDe(Modulo.deNombreVisible(nombreModulo)).abrir();
    }

    /** Ejemplo: {@code When elige "Configuración" en el menú} → clic en ese módulo del menú lateral. */
    @When("elige {string} en el menú")
    public void eligeEnElMenu(String nombreModulo) {
        marco.irA(Modulo.deNombreVisible(nombreModulo));
    }

    /** Ejemplo: {@code Then visualiza el módulo de "Configuración"} → marcado en el menú, cargado y con sus datos. */
    @Then("visualiza el módulo de {string}")
    public void visualizaElModuloDe(String nombreModulo) {
        pantallaDe(Modulo.deNombreVisible(nombreModulo)).esperarQueEsteLista();
    }

    private PantallaBase pantallaDe(Modulo modulo) {
        return switch (modulo) {
            case CONFIGURACION -> configuracion;
            case INVENTARIO, RESERVAS, CAJA, MOVIMIENTOS ->
                    throw new PendingException("El módulo " + modulo.nombreVisible() + " todavía no tiene pantalla en la v2.");
        };
    }
}
