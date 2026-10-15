package com.tcg.bot.configuracion;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Columnas de la pestaña del inventario (A:K) y los nombres con los que se las reconoce en una planilla ajena (alias).
 * Copia de los alias de la v1 ({@code legacy/service/GoogleSheetsService}, {@code SheetColumns}), sin depender de ella:
 * si cambian en un lado, hay que cambiarlos en el otro hasta el corte.
 * La comparación ignora mayúsculas, tildes y signos: "Código de Set" = "codigo de set".
 */
public final class ColumnasInventario {

    /** Encabezados de la pestaña del inventario, en orden (los mismos que escribe la v1, columnas A:K). */
    public static final List<String> ENCABEZADOS = List.of("Cantidad", "Nombre", "Codigo de set", "Nombre del set",
            "Numero de carta", "Condicion", "Printing (foil/no foil)", "Idioma", "Precio Local", "Precio CK USD", "Accion");

    private static final int COLUMNA_NOMBRE = 1;

    /** Alias de cada columna, en el mismo orden que {@link #ENCABEZADOS}. */
    private static final List<List<String>> ALIAS = List.of(
            List.of("Cantidad", "Stock", "Qty", "Quantity"),
            List.of("Nombre", "Carta", "Card", "Name"),
            List.of("Codigo de set", "Codigo set", "Set code", "SetCode", "Code"),
            List.of("Set Name", "Nombre del set", "Nombre set", "Set", "Edition", "Edicion"),
            List.of("Card Number", "Numero de carta", "Numero carta", "Numero", "N", "No", "Number", "Collector number", "Collector"),
            List.of("Condicion", "Condition", "Estado carta"),
            List.of("Printing", "Foil", "Nonfoil", "Printing foil/no foil", "Printing (foil/no foil)"),
            List.of("Idioma", "Language", "Lang"),
            List.of("Precio Local", "Precio Local ARS", "Precio local", "Precio local ARS", "Precio", "ARS", "Price"),
            List.of("Precio CK USD", "CK USD", "Card Kingdom USD", "CK Price", "USD"),
            List.of("Accion", "Action", "Estado", "Stock status"));

    /** Alias normalizado → número de columna de la app. */
    private static final Map<String, Integer> COLUMNA_POR_ALIAS = columnaPorAlias();

    private ColumnasInventario() {
    }

    /**
     * Pasa un encabezado a la forma con la que se comparan los alias: sin tildes, en minúscula, solo letras y números.
     * Ejemplo: {@code normalizar(" Código de  Set ")} → {@code "codigo de set"}; {@code normalizar("Printing (foil/no foil)")} → {@code "printing foil no foil"}.
     */
    public static String normalizar(String encabezado) {
        if (encabezado == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(encabezado, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /**
     * Número de columna de la app (0 = Cantidad … 10 = Accion) que corresponde a un encabezado, o -1 si no se reconoce.
     * Ejemplo: {@code columnaDeLaApp("Qty")} → {@code 0}; {@code columnaDeLaApp("Notas")} → {@code -1}.
     */
    public static int columnaDeLaApp(String encabezado) {
        return COLUMNA_POR_ALIAS.getOrDefault(normalizar(encabezado), -1);
    }

    /**
     * Si una fila de encabezados es de inventario: se reconoce la columna Nombre y al menos otra columna de la app.
     * Ejemplo: {@code ["Carta", "Stock", "Notas"]} → {@code true}; {@code ["Fecha", "Total"]} → {@code false}; {@code ["Nombre"]} → {@code false}.
     */
    public static boolean tieneEncabezadosDeInventario(List<String> encabezados) {
        boolean tieneNombre = false;
        boolean tieneOtra = false;
        for (String encabezado : encabezados) {
            int columna = columnaDeLaApp(encabezado);
            if (columna == COLUMNA_NOMBRE) {
                tieneNombre = true;
            } else if (columna >= 0) {
                tieneOtra = true;
            }
        }
        return tieneNombre && tieneOtra;
    }

    /**
     * Pasa las filas de una pestaña cualquiera (fila 1 = encabezados) al formato de la pestaña del inventario:
     * las 11 columnas de la app en su orden y, al final, las que no se reconocen (con su encabezado original).
     * Si dos columnas corresponden al mismo dato, manda la primera y la otra pasa al final. Las columnas sin encabezado
     * ni datos y las filas vacías se descartan.
     * Ejemplo: {@code [["Carta", "Stock", "Notas"], [ "Sol Ring", 4, "firmada"], [], ["Opt", 2, ""]]} →
     * filas {@code [[Cantidad, Nombre, …, Accion, "Notas"], [4, "Sol Ring", "", …, "firmada"], [2, "Opt", …, ""]]},
     * {@code cantidad = 2}, {@code hayColumnasExtra = true}.
     */
    public static CartasConvertidas convertir(List<List<Object>> filasOrigen) {
        List<Object> encabezadosOrigen = filasOrigen.isEmpty() ? List.of() : filasOrigen.get(0);
        int anchoOrigen = filasOrigen.stream().mapToInt(List::size).max().orElse(0);

        int[] origenDeColumnaApp = new int[ENCABEZADOS.size()];
        Arrays.fill(origenDeColumnaApp, -1);
        List<Integer> columnasExtra = new ArrayList<>();
        for (int columna = 0; columna < anchoOrigen; columna++) {
            String encabezado = texto(celda(encabezadosOrigen, columna));
            int deLaApp = encabezado.isBlank() ? -1 : columnaDeLaApp(encabezado);
            if (deLaApp >= 0 && origenDeColumnaApp[deLaApp] < 0) {
                origenDeColumnaApp[deLaApp] = columna;
            } else if (!encabezado.isBlank() || columnaTieneDatos(filasOrigen, columna)) {
                columnasExtra.add(columna);
            }
        }

        List<List<Object>> filas = new ArrayList<>();
        List<Object> encabezados = new ArrayList<>(ENCABEZADOS);
        for (int columna : columnasExtra) {
            encabezados.add(texto(celda(encabezadosOrigen, columna)));
        }
        filas.add(encabezados);

        int cantidad = 0;
        for (int numeroFila = 1; numeroFila < filasOrigen.size(); numeroFila++) {
            List<Object> filaOrigen = filasOrigen.get(numeroFila);
            if (filaOrigen.stream().allMatch(valor -> texto(valor).isBlank())) {
                continue;
            }
            List<Object> fila = new ArrayList<>();
            for (int origen : origenDeColumnaApp) {
                fila.add(origen < 0 ? "" : valorOVacio(celda(filaOrigen, origen)));
            }
            for (int columna : columnasExtra) {
                fila.add(valorOVacio(celda(filaOrigen, columna)));
            }
            filas.add(fila);
            cantidad++;
        }
        return new CartasConvertidas(filas, cantidad, !columnasExtra.isEmpty());
    }

    private static boolean columnaTieneDatos(List<List<Object>> filas, int columna) {
        for (int numeroFila = 1; numeroFila < filas.size(); numeroFila++) {
            if (!texto(celda(filas.get(numeroFila), columna)).isBlank()) {
                return true;
            }
        }
        return false;
    }

    private static Object celda(List<Object> fila, int columna) {
        return columna < fila.size() ? fila.get(columna) : null;
    }

    private static Object valorOVacio(Object valor) {
        return valor == null ? "" : valor;
    }

    private static String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor);
    }

    private static Map<String, Integer> columnaPorAlias() {
        Map<String, Integer> columnas = new HashMap<>();
        for (int columna = 0; columna < ALIAS.size(); columna++) {
            for (String alias : ALIAS.get(columna)) {
                columnas.putIfAbsent(normalizar(alias), columna);
            }
        }
        return Map.copyOf(columnas);
    }

    /**
     * Resultado de {@link #convertir}: las filas listas para escribir (con encabezados), cuántas cartas (filas) hay y si
     * quedaron columnas desconocidas al final.
     * Ejemplo: {@code new CartasConvertidas(filas, 128, true)}.
     */
    public record CartasConvertidas(List<List<Object>> filas, int cantidad, boolean hayColumnasExtra) {
    }
}
