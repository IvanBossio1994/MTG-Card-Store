package com.tcg.bot.configuracion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Alias de columnas y conversión de "Traer cartas de" (HU-006 CA1 y CA7). */
class ColumnasInventarioTest {

    @Test
    void normalizaSinTildesMayusculasNiSignos() {
        assertThat(ColumnasInventario.normalizar(" Código de  Set ")).isEqualTo("codigo de set");
        assertThat(ColumnasInventario.normalizar("Printing (foil/no foil)")).isEqualTo("printing foil no foil");
        assertThat(ColumnasInventario.normalizar(null)).isEmpty();
    }

    @Test
    void reconoceLasColumnasPorSusAlias() {
        assertThat(ColumnasInventario.columnaDeLaApp("Qty")).isZero();
        assertThat(ColumnasInventario.columnaDeLaApp("CARD")).isEqualTo(1);
        assertThat(ColumnasInventario.columnaDeLaApp("Edición")).isEqualTo(3);
        assertThat(ColumnasInventario.columnaDeLaApp("Estado carta")).isEqualTo(5);
        assertThat(ColumnasInventario.columnaDeLaApp("Estado")).isEqualTo(10);
        assertThat(ColumnasInventario.columnaDeLaApp("Notas")).isEqualTo(-1);
    }

    @Test
    void esDeInventarioSiTieneNombreYOtraColumnaDeLaApp() {
        assertThat(ColumnasInventario.tieneEncabezadosDeInventario(List.of("Carta", "Stock", "Notas"))).isTrue();
        assertThat(ColumnasInventario.tieneEncabezadosDeInventario(List.of("Nombre"))).isFalse();
        assertThat(ColumnasInventario.tieneEncabezadosDeInventario(List.of("Stock", "Precio"))).isFalse();
        assertThat(ColumnasInventario.tieneEncabezadosDeInventario(List.of())).isFalse();
    }

    @Test
    void ordenaLasColumnasDeLaAppYDejaLasDesconocidasAlFinal() {
        var convertidas = ColumnasInventario.convertir(List.of(
                List.of("Notas", "Name", "Qty", "Nombre", "", ""),
                List.of("firmada", "Sol Ring", 4, "Anillo", "", "x"),
                List.of("", "", "", "", "", ""),
                List.of("", "Opt", 2)));

        assertThat(convertidas.cantidad()).isEqualTo(2);
        assertThat(convertidas.hayColumnasExtra()).isTrue();
        var filas = convertidas.filas();
        // Extras en el orden del origen: "Notas", el "Nombre" duplicado y la columna sin encabezado pero con datos.
        assertThat(filas.get(0)).hasSize(14).endsWith("Notas", "Nombre", "");
        assertThat(filas.get(1)).containsExactly(4, "Sol Ring", "", "", "", "", "", "", "", "", "", "firmada", "Anillo", "x");
        assertThat(filas.get(2)).containsExactly(2, "Opt", "", "", "", "", "", "", "", "", "", "", "", "");
    }

    @Test
    void sinColumnasDesconocidasNiCartasSoloQuedanLosEncabezados() {
        var convertidas = ColumnasInventario.convertir(List.of(List.of("Cantidad", "Nombre")));

        assertThat(convertidas.cantidad()).isZero();
        assertThat(convertidas.hayColumnasExtra()).isFalse();
        assertThat(convertidas.filas()).hasSize(1);
        assertThat(convertidas.filas().get(0)).containsExactlyElementsOf(ColumnasInventario.ENCABEZADOS);
    }
}
