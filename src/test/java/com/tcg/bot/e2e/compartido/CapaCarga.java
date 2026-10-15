package com.tcg.bot.e2e.compartido;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.tcg.bot.e2e.localizadores.AppLocalizadores;
import com.tcg.bot.e2e.soporte.NavegadorE2E;

/**
 * La capa de carga con el gatito (espeja {@code frontend/src/compartido/componentes/CapaCarga.tsx}), que tapa la pantalla
 * mientras se carga o se habla con Google.
 * Ejemplo: {@code capaCarga.esperarQueDesaparezca()} → sigue cuando la pantalla ya cargó sus datos.
 */
public class CapaCarga {

    private final NavegadorE2E navegador;

    /**
     * Crea la capa sobre el navegador del escenario. Ejemplo: {@code new CapaCarga(navegador)}.
     */
    public CapaCarga(NavegadorE2E navegador) {
        this.navegador = navegador;
    }

    /**
     * Espera a que terminen los pedidos a la API y a que la capa ya no se vea.
     * Ejemplo: recién abierta Configuración → espera a que se cargue la configuración y se vaya el gatito.
     */
    public void esperarQueDesaparezca() {
        navegador.pagina().waitForLoadState(LoadState.NETWORKIDLE);
        capa().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN));
    }

    private Locator capa() {
        return navegador.pagina().getByTestId(AppLocalizadores.CAPA_CARGA);
    }
}
