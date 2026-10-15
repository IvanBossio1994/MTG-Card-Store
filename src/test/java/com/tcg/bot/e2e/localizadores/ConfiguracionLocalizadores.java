package com.tcg.bot.e2e.localizadores;

/**
 * {@code data-testid} de la pantalla Configuración, copiados de docs/v2/locators.md. Los mensajes por bloque o por campo
 * se arman con {@code PantallaBase} ({@code configuracion-mensaje-<tipo>-<bloque>}).
 * Ejemplo: {@code pagina.getByTestId(ConfiguracionLocalizadores.CAMPO_NOMBRE_TIENDA).fill("Codex Live")}.
 */
public final class ConfiguracionLocalizadores {

    /** Pantalla de los locators y de los mensajes. */
    public static final String PANTALLA = "configuracion";

    public static final String BOTON_REINTENTAR = "configuracion-boton-reintentar";
    public static final String ENLACE_INICIAR_SESION_GOOGLE = "configuracion-enlace-iniciar-sesion-google";

    // Tienda
    public static final String CAMPO_NOMBRE_TIENDA = "configuracion-campo-nombre-tienda";
    public static final String BOTON_SUBIR_LOGO = "configuracion-boton-subir-logo";
    public static final String BOTON_QUITAR_LOGO = "configuracion-boton-quitar-logo";
    public static final String BOTON_GUARDAR_TIENDA = "configuracion-boton-guardar-tienda";

    // Sheet (inventario)
    public static final String CAMPO_SHEET = "configuracion-campo-sheet";
    public static final String CAMPO_PESTANA_INVENTARIO = "configuracion-campo-pestana-inventario";
    public static final String SELECTOR_TRAER_CARTAS_DE = "configuracion-selector-traer-cartas-de";
    public static final String TEXTO_AYUDA_TRAER_CARTAS_DE = "configuracion-texto-ayuda-traer-cartas-de";
    public static final String BOTON_REINTENTAR_PESTANAS_ORIGEN = "configuracion-boton-reintentar-pestanas-origen";
    public static final String BOTON_GUARDAR_SHEET = "configuracion-boton-guardar-sheet";
    public static final String TEXTO_GMAIL_CONECTADO = "configuracion-texto-gmail-conectado";

    // Renombrar una pestaña
    public static final String SELECTOR_PESTANA_A_RENOMBRAR = "configuracion-selector-pestana-a-renombrar";
    public static final String CAMPO_NOMBRE_NUEVO = "configuracion-campo-nombre-nuevo";
    public static final String BOTON_RENOMBRAR_PESTANA = "configuracion-boton-renombrar-pestana";
    public static final String BOTON_REINTENTAR_PESTANAS = "configuracion-boton-reintentar-pestanas";

    // Precios
    public static final String CAMPO_COTIZACION = "configuracion-campo-cotizacion";
    public static final String CAMPO_REDONDEO = "configuracion-campo-redondeo";
    public static final String CAMPO_BULK_COMMON = "configuracion-campo-bulk-common";
    public static final String CAMPO_BULK_UNCOMMON = "configuracion-campo-bulk-uncommon";
    public static final String CAMPO_BULK_RARE = "configuracion-campo-bulk-rare";
    public static final String CAMPO_BULK_MYTHIC = "configuracion-campo-bulk-mythic";
    public static final String CAMPO_BULK_TIERRA_BASICA = "configuracion-campo-bulk-tierra-basica";
    public static final String CAMPO_CARPETA_CACHE = "configuracion-campo-carpeta-cache";
    public static final String BOTON_GUARDAR_PRECIOS = "configuracion-boton-guardar-precios";

    // Puntos
    public static final String CAMPO_PESOS_POR_PUNTO = "configuracion-campo-pesos-por-punto";
    public static final String TEXTO_EJEMPLO_PUNTOS = "configuracion-texto-ejemplo-puntos";
    public static final String BOTON_GUARDAR_PUNTOS = "configuracion-boton-guardar-puntos";

    // Modal de cambios sin guardar
    public static final String BOTON_SEGUIR_EDITANDO = "configuracion-boton-seguir-editando";
    public static final String BOTON_SALIR_IGUAL = "configuracion-boton-salir-igual";

    private ConfiguracionLocalizadores() {
    }
}
