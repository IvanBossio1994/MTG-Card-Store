package com.tcg.bot.e2e.compartido;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.tcg.bot.e2e.compartido.MarcoApp.Modulo;
import com.tcg.bot.e2e.soporte.NavegadorE2E;

/**
 * Base liviana de todas las {@code <Modulo>Pantalla} (regla 8 de estructura-carpetas.md): abrir el módulo, esperar que
 * termine la capa de carga y leer el mensaje de un bloque o el error de un campo. Los elementos se ubican solo por
 * {@code data-testid}. Ejemplo: {@code new ConfiguracionPantalla(navegador).abrir()}.
 */
public abstract class PantallaBase {

    /** Tipo de mensaje de un bloque, como en el locator {@code <pantalla>-mensaje-<tipo>-<bloque>}. */
    public enum TipoMensaje {
        EXITO,
        AVISO,
        ERROR;

        String enLocator() {
            return name().toLowerCase();
        }
    }

    protected final NavegadorE2E navegador;
    protected final MarcoApp marco;
    protected final CapaCarga capaCarga;
    private final Modulo modulo;
    private final String pantalla;

    /**
     * Ejemplo: {@code super(navegador, Modulo.CONFIGURACION, "configuracion")}.
     */
    protected PantallaBase(NavegadorE2E navegador, Modulo modulo, String pantalla) {
        this.navegador = navegador;
        this.marco = new MarcoApp(navegador);
        this.capaCarga = new CapaCarga(navegador);
        this.modulo = modulo;
        this.pantalla = pantalla;
    }

    /**
     * Elemento que solo aparece cuando la pantalla cargó bien sus datos (no en la carga ni en el error de carga).
     * Ejemplo: en Configuración, el campo del nombre de la tienda.
     */
    protected abstract Locator elementoDePantallaLista();

    /**
     * Abre el módulo por su dirección y espera que esté lista. Ejemplo: {@code abrir()} → /app/configuracion cargada.
     */
    public void abrir() {
        pagina().navigate(modulo.ruta());
        esperarQueEsteLista();
    }

    /**
     * Espera que el módulo esté marcado en el menú, que termine la capa de carga y que se vean sus datos.
     * Ejemplo: después de hacer clic en "Configuración" del menú → sigue cuando se ve el formulario.
     */
    public void esperarQueEsteLista() {
        marco.esperarModuloActivo(modulo);
        capaCarga.esperarQueDesaparezca();
        assertThat(elementoDePantallaLista()).isVisible();
    }

    /**
     * Espera el mensaje de un bloque y devuelve su texto.
     * Ejemplo: {@code leerMensajeDeBloque(EXITO, "tienda")} → {@code "Tienda guardada."}; si no aparece, falla por tiempo.
     */
    public String leerMensajeDeBloque(TipoMensaje tipo, String bloque) {
        return textoVisible(pantalla + "-mensaje-" + tipo.enLocator() + "-" + bloque);
    }

    /**
     * Espera el error junto a un campo y devuelve su texto.
     * Ejemplo: {@code leerErrorDeCampo("cotizacion")} → {@code "Ingresá la cotización del dólar."}.
     */
    public String leerErrorDeCampo(String campo) {
        return textoVisible(pantalla + "-mensaje-error-" + campo);
    }

    /** Marco de la app (menú lateral). Ejemplo: {@code marco().irA(Modulo.CONFIGURACION)}. */
    public MarcoApp marco() {
        return marco;
    }

    /** Pestaña del escenario. */
    protected Page pagina() {
        return navegador.pagina();
    }

    /** Ubica un elemento por su {@code data-testid}. Ejemplo: {@code porTestId("configuracion-boton-guardar-tienda")}. */
    protected Locator porTestId(String testId) {
        return pagina().getByTestId(testId);
    }

    private String textoVisible(String testId) {
        Locator elemento = porTestId(testId);
        assertThat(elemento).isVisible();
        return elemento.innerText().trim();
    }
}
