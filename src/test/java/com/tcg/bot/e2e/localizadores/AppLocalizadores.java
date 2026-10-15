package com.tcg.bot.e2e.localizadores;

/**
 * {@code data-testid} del marco de la app (pantalla {@code app}: menú lateral), según docs/v2/locators.md.
 * Ejemplo: {@code pagina.getByTestId(AppLocalizadores.ENLACE_CONFIGURACION).click()}.
 */
public final class AppLocalizadores {

    public static final String ENLACE_INVENTARIO = "app-enlace-inventario";
    public static final String ENLACE_RESERVAS = "app-enlace-reservas";
    public static final String ENLACE_CAJA = "app-enlace-caja";
    public static final String ENLACE_MOVIMIENTOS = "app-enlace-movimientos";
    /** Enlace del menú a Configuración; el del módulo actual lleva {@code aria-current="page"}. */
    public static final String ENLACE_CONFIGURACION = "app-enlace-configuracion";

    /** Capa de carga con el gatito ({@code CapaCarga}), que tapa la pantalla mientras se carga o se habla con Google. */
    public static final String CAPA_CARGA = "app-capa-carga";

    private AppLocalizadores() {
    }
}
