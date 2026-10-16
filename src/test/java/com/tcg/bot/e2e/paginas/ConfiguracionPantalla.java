package com.tcg.bot.e2e.paginas;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.SelectOption;
import com.tcg.bot.e2e.compartido.MarcoApp.Modulo;
import com.tcg.bot.e2e.compartido.PantallaBase;
import com.tcg.bot.e2e.localizadores.ConfiguracionLocalizadores;
import com.tcg.bot.e2e.soporte.NavegadorE2E;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Page Object de Configuración: todas las acciones y lecturas de la pantalla (qa-guia.md). Los pasos hablan con el texto
 * visible (etiqueta del campo, nombre del botón) y acá se traduce a su {@code data-testid}.
 * Ejemplo: {@code configuracion.abrir()} → /app/configuracion abierta y con sus datos cargados.
 */
public class ConfiguracionPantalla extends PantallaBase {

    /** Guardar habla con Google (crea pestañas, trae cartas) y con el límite de lecturas puede tardar más de los 5 s comunes. */
    private static final double PLAZO_GOOGLE_MS = 45_000;

    private static final Map<String, String> CAMPOS = Map.ofEntries(
            Map.entry("Nombre de la tienda", ConfiguracionLocalizadores.CAMPO_NOMBRE_TIENDA),
            Map.entry("ID o URL del Sheet", ConfiguracionLocalizadores.CAMPO_SHEET),
            Map.entry("Pestaña del inventario", ConfiguracionLocalizadores.CAMPO_PESTANA_INVENTARIO),
            Map.entry("Traer cartas de", ConfiguracionLocalizadores.SELECTOR_TRAER_CARTAS_DE),
            Map.entry("Pestaña a renombrar", ConfiguracionLocalizadores.SELECTOR_PESTANA_A_RENOMBRAR),
            Map.entry("Nombre nuevo", ConfiguracionLocalizadores.CAMPO_NOMBRE_NUEVO),
            Map.entry("Cotización (ARS por USD)", ConfiguracionLocalizadores.CAMPO_COTIZACION),
            Map.entry("Pesos por punto ($)", ConfiguracionLocalizadores.CAMPO_PESOS_POR_PUNTO));

    private static final Map<String, String> BOTONES = Map.ofEntries(
            Map.entry("Guardar tienda", ConfiguracionLocalizadores.BOTON_GUARDAR_TIENDA),
            Map.entry("Guardar inventario", ConfiguracionLocalizadores.BOTON_GUARDAR_SHEET),
            Map.entry("Renombrar pestaña", ConfiguracionLocalizadores.BOTON_RENOMBRAR_PESTANA),
            Map.entry("Guardar precios", ConfiguracionLocalizadores.BOTON_GUARDAR_PRECIOS),
            Map.entry("Guardar puntos", ConfiguracionLocalizadores.BOTON_GUARDAR_PUNTOS),
            Map.entry("Seguir editando", ConfiguracionLocalizadores.BOTON_SEGUIR_EDITANDO),
            Map.entry("Salir igual", ConfiguracionLocalizadores.BOTON_SALIR_IGUAL));

    /**
     * Cucumber la crea por escenario con el navegador de ese escenario. Ejemplo: {@code new ConfiguracionPantalla(navegador)}.
     */
    public ConfiguracionPantalla(NavegadorE2E navegador) {
        super(navegador, Modulo.CONFIGURACION, ConfiguracionLocalizadores.PANTALLA);
    }

    @Override
    protected Locator elementoDePantallaLista() {
        return porTestId(ConfiguracionLocalizadores.CAMPO_NOMBRE_TIENDA);
    }

    /** Ejemplo: {@code escribirEn("Nombre de la tienda", "Codex Live Store")} → reemplaza lo que había en el campo. */
    public void escribirEn(String etiqueta, String valor) {
        campo(etiqueta).fill(valor);
    }

    /**
     * Pega un link en el campo del Sheet y sale del campo (la app lista sus pestañas al salir); espera que la lista
     * "Traer cartas de" vuelva a estar disponible. Ejemplo: {@code pegarLinkDelSheet(SheetDeQa.LINK)}.
     */
    public void pegarLinkDelSheet(String link) {
        Locator campoLink = campo("ID o URL del Sheet");
        campoLink.fill(link);
        campoLink.blur();
        assertThat(campo("Traer cartas de")).isEnabled();
    }

    /** Ejemplo: {@code hacerClicEn("Guardar tienda")}. */
    public void hacerClicEn(String boton) {
        String testId = BOTONES.get(boton);
        if (testId == null) {
            throw new IllegalArgumentException("Botón desconocido \"" + boton + "\". Válidos: " + BOTONES.keySet());
        }
        porTestId(testId).click();
    }

    /** Ejemplo: {@code elegirOpcion("Traer cartas de", "No traer cartas")}; espera que la lista esté habilitada. */
    public void elegirOpcion(String lista, String opcion) {
        Locator selector = campo(lista);
        assertThat(selector).isEnabled();
        selector.selectOption(new SelectOption().setLabel(opcion));
    }

    /** Espera un éxito o un aviso que contenga el texto. Ejemplo: {@code esperarMensaje("Tienda guardada.")}. */
    public void esperarMensaje(String mensaje) {
        try {
            assertThat(pagina().getByTestId(Pattern.compile("^configuracion-mensaje-(exito|aviso)-"))
                            .filter(new Locator.FilterOptions().setHasText(mensaje))
                            .first())
                    .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(PLAZO_GOOGLE_MS));
        } catch (AssertionError falla) {
            // Dice qué mensajes había en pantalla, para distinguir un texto distinto de un mensaje que no apareció.
            throw new AssertionError(
                    "Se esperaba el mensaje \"" + mensaje + "\" y en pantalla había: "
                            + pagina().getByTestId(Pattern.compile("^configuracion-mensaje-"))
                                    .allInnerTexts(),
                    falla);
        }
    }

    /** Espera un error (de campo o de bloque) que contenga el texto. */
    public void esperarError(String mensaje) {
        assertThat(pagina().getByTestId(Pattern.compile("^configuracion-mensaje-error-"))
                        .filter(new Locator.FilterOptions().setHasText(mensaje))
                        .first())
                .isVisible();
    }

    /** Ejemplo: {@code esperarValorDelCampo("Pestaña del inventario", "Stock 2026")}. Sirve para listas (valor = opción). */
    public void esperarValorDelCampo(String etiqueta, String valor) {
        assertThat(campo(etiqueta)).hasValue(valor);
    }

    /** Espera que la lista tenga elegida esa opción (texto visible). Ejemplo: "stock viejo · sugerida" en "Traer cartas de". */
    public void esperarOpcionElegida(String lista, String opcion) {
        assertThat(campo(lista).locator("option:checked")).hasText(opcion);
    }

    /** Espera que la opción de la lista se vea, pero no se pueda elegir. */
    public void esperarOpcionDeshabilitada(String opcion, String lista) {
        try {
            assertThat(campo(lista)
                            .locator("option", new Locator.LocatorOptions().setHasText(opcion))
                            .first())
                    .isDisabled();
        } catch (AssertionError falla) {
            throw new AssertionError(
                    "Se esperaba la opción \"" + opcion + "\" deshabilitada y la lista tiene: "
                            + campo(lista).locator("option").allInnerTexts(),
                    falla);
        }
    }

    /** Espera el título de un bloque de la pantalla. Ejemplo: {@code esperarBloque("Regla de precio")}. */
    public void esperarBloque(String titulo) {
        assertThat(pagina().getByRole(
                                AriaRole.HEADING,
                                new com.microsoft.playwright.Page.GetByRoleOptions()
                                        .setName(titulo)
                                        .setExact(true)))
                .isVisible();
    }

    /** Espera "Conectado como …" junto al bloque del Sheet. */
    public void esperarGmailConectado() {
        assertThat(porTestId(ConfiguracionLocalizadores.TEXTO_GMAIL_CONECTADO)).containsText("Conectado como");
    }

    /** Espera el ejemplo en vivo de puntos. Ejemplo: "Una compra de $25.000 suma 50 puntos.". */
    public void esperarEjemploDePuntos(String texto) {
        assertThat(porTestId(ConfiguracionLocalizadores.TEXTO_EJEMPLO_PUNTOS)).containsText(texto);
    }

    /** Espera el cartel de cambios sin guardar. Ejemplo: {@code esperarCartel("Hay cambios sin guardar")}. */
    public void esperarCartel(String titulo) {
        assertThat(pagina().getByText(titulo)).isVisible();
        assertThat(porTestId(ConfiguracionLocalizadores.BOTON_SEGUIR_EDITANDO)).isVisible();
    }

    private Locator campo(String etiqueta) {
        String testId = CAMPOS.get(etiqueta);
        if (testId == null) {
            throw new IllegalArgumentException("Campo desconocido \"" + etiqueta + "\". Válidos: " + CAMPOS.keySet());
        }
        return porTestId(testId);
    }
}
