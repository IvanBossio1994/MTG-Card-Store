package com.tcg.bot.e2e.pasos;

import static org.assertj.core.api.Assertions.assertThat;

import com.tcg.bot.e2e.paginas.ConfiguracionPantalla;
import com.tcg.bot.e2e.soporte.SheetDeQa;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pasos de Configuración: solo llaman a la pantalla y al Sheet de QA, sin locators ni lógica.
 * Ejemplo: {@code When escribe "Codex Live Store" en "Nombre de la tienda"}.
 */
public class ConfiguracionPasos {

    private final ConfiguracionPantalla configuracion;
    private final SheetDeQa sheetDeQa;

    /** Cucumber lo crea por escenario. Ejemplo: {@code new ConfiguracionPasos(pantalla, sheetDeQa)}. */
    public ConfiguracionPasos(ConfiguracionPantalla configuracion, SheetDeQa sheetDeQa) {
        this.configuracion = configuracion;
        this.sheetDeQa = sheetDeQa;
    }

    // ---------- Datos del Sheet de QA ----------

    /** Ejemplo: {@code Given el Sheet de QA tiene solo la pestaña "Hoja 1"} → una pestaña vacía. */
    @Given("el Sheet de QA tiene solo la pestaña {string}")
    public void sheetConSoloLaPestana(String pestana) {
        sheetDeQa.dejarConPestanas(Map.of(pestana, List.of()));
    }

    /** Ejemplo: tabla con una pestaña por fila → el Sheet queda con esas pestañas, vacías y en ese orden. */
    @Given("el Sheet de QA tiene las pestañas vacías:")
    public void sheetConPestanasVacias(DataTable pestanas) {
        Map<String, List<List<Object>>> mapa = new LinkedHashMap<>();
        pestanas.asList().forEach(nombre -> mapa.put(nombre, List.of()));
        sheetDeQa.dejarConPestanas(mapa);
    }

    /** Ejemplo: tabla con encabezados y cartas → el Sheet queda con esa única pestaña y esas filas. */
    @Given("el Sheet de QA tiene solo la pestaña {string} con las cartas:")
    public void sheetConCartas(String pestana, DataTable cartas) {
        List<List<Object>> filas = new ArrayList<>();
        for (List<String> fila : cartas.asLists()) {
            List<Object> celdas = new ArrayList<>();
            for (String celda : fila) {
                celdas.add(celda.matches("\\d+") ? (Object) Integer.valueOf(celda) : celda);
            }
            filas.add(celdas);
        }
        sheetDeQa.dejarConPestanas(Map.of(pestana, filas));
    }

    /** Ejemplo: {@code Then valida que el Sheet de QA tenga la pestaña "Inventario"}. */
    @Then("valida que el Sheet de QA tenga la pestaña {string}")
    public void sheetTieneLaPestana(String pestana) {
        assertThat(sheetDeQa.pestanas()).contains(pestana);
    }

    /** Ejemplo: {@code Then valida que el Sheet de QA no tenga la pestaña "Hoja 1"}. */
    @Then("valida que el Sheet de QA no tenga la pestaña {string}")
    public void sheetNoTieneLaPestana(String pestana) {
        assertThat(sheetDeQa.pestanas()).doesNotContain(pestana);
    }

    // ---------- Acciones ----------

    /** Ejemplo: {@code When escribe "Codex Live Store" en "Nombre de la tienda"}. */
    @When("escribe {string} en {string}")
    public void escribeEn(String valor, String campo) {
        configuracion.escribirEn(campo, valor);
    }

    /** Ejemplo: {@code When pega el link del Sheet de QA}. */
    @When("pega el link del Sheet de QA")
    public void pegaElLinkDelSheetDeQa() {
        configuracion.pegarLinkDelSheet(SheetDeQa.LINK);
    }

    /** Ejemplo: {@code When elige "No traer cartas" en "Traer cartas de"}. */
    @When("elige {string} en {string}")
    public void eligeEn(String opcion, String lista) {
        configuracion.elegirOpcion(lista, opcion);
    }

    /** Ejemplo: {@code When hace clic en "Guardar tienda"}. */
    @When("hace clic en {string}")
    public void haceClicEn(String boton) {
        configuracion.hacerClicEn(boton);
    }

    // ---------- Lo que se ve ----------

    /** Ejemplo: {@code Then visualiza el bloque "Regla de precio"}. */
    @Then("visualiza el bloque {string}")
    public void visualizaElBloque(String titulo) {
        configuracion.esperarBloque(titulo);
    }

    /** Ejemplo: {@code Then visualiza la cuenta de Gmail conectada}. */
    @Then("visualiza la cuenta de Gmail conectada")
    public void visualizaGmailConectado() {
        configuracion.esperarGmailConectado();
    }

    /** Ejemplo: {@code Then visualiza el mensaje "Tienda guardada."} (éxito o aviso). */
    @Then("visualiza el mensaje {string}")
    public void visualizaElMensaje(String mensaje) {
        configuracion.esperarMensaje(mensaje);
    }

    /** Ejemplo: {@code Then visualiza el error "El nombre nuevo es obligatorio."}. */
    @Then("visualiza el error {string}")
    public void visualizaElError(String mensaje) {
        configuracion.esperarError(mensaje);
    }

    /** Ejemplo: {@code Then visualiza el ejemplo "Una compra de $25.000 suma 50 puntos."}. */
    @Then("visualiza el ejemplo {string}")
    public void visualizaElEjemplo(String texto) {
        configuracion.esperarEjemploDePuntos(texto);
    }

    /** Ejemplo: {@code Then el campo "Nombre nuevo" muestra ""}. */
    @Then("el campo {string} muestra {string}")
    public void elCampoMuestra(String campo, String valor) {
        configuracion.esperarValorDelCampo(campo, valor);
    }

    /** Ejemplo: {@code Then la lista "Traer cartas de" tiene elegido "stock viejo · sugerida"}. */
    @Then("la lista {string} tiene elegido {string}")
    public void laListaTieneElegido(String lista, String opcion) {
        configuracion.esperarOpcionElegida(lista, opcion);
    }

    /** Ejemplo: {@code Then la opción "Caja" de "Pestaña a renombrar" está deshabilitada}. */
    @Then("la opción {string} de {string} está deshabilitada")
    public void laOpcionEstaDeshabilitada(String opcion, String lista) {
        configuracion.esperarOpcionDeshabilitada(opcion, lista);
    }

    /** Ejemplo: {@code Then visualiza el cartel "Hay cambios sin guardar"} con "Seguir editando". */
    @Then("visualiza el cartel {string}")
    public void visualizaElCartel(String titulo) {
        configuracion.esperarCartel(titulo);
    }
}
