package com.tcg.bot.compartido;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sheets simulados en memoria, para los tests y el modo prueba (perfil {@code e2e}). No usa Google ni internet.
 * Solo se puede entrar a los Sheets "compartidos" con {@link #compartir}; cualquier otro ID responde 403, como un Sheet
 * no compartido con la cuenta de servicio. Cada pestaña guarda sus filas (la fila 1 son los encabezados).
 */
public class AccesoSheetEnMemoria implements AccesoSheet {

    private final Map<String, LinkedHashMap<String, List<List<Object>>>> sheets = new ConcurrentHashMap<>();

    /**
     * Da de alta un Sheet compartido con la cuenta de servicio, con sus pestañas vacías (sin encabezados).
     * Ejemplo: {@code compartir("1Abc…", "Hoja 1", "Ventas")} → {@code listarPestanas("1Abc…")} devuelve esas dos.
     */
    public AccesoSheetEnMemoria compartir(String sheetId, String... pestanas) {
        LinkedHashMap<String, List<List<Object>>> nuevas = new LinkedHashMap<>();
        for (String pestana : pestanas) {
            nuevas.put(pestana, new ArrayList<>());
        }
        sheets.put(sheetId, nuevas);
        return this;
    }

    /**
     * Reemplaza todas las filas de una pestaña existente (la primera son los encabezados). Sirve para preparar datos.
     * Ejemplo: {@code cargarFilas(id, "stock viejo", List.of(List.of("Cantidad", "Carta"), List.of(4, "Sol Ring")))}.
     */
    public synchronized AccesoSheetEnMemoria cargarFilas(String sheetId, String pestana, List<List<Object>> filas) {
        LinkedHashMap<String, List<List<Object>>> pestanas = sheet(sheetId);
        if (!pestanas.containsKey(pestana)) {
            throw AccesoSheet.pestanaInexistente();
        }
        pestanas.put(pestana, copia(filas));
        return this;
    }

    @Override
    public synchronized List<String> listarPestanas(String sheetId) {
        return List.copyOf(sheet(sheetId).keySet());
    }

    @Override
    public synchronized void renombrarPestana(String sheetId, String nombreActual, String nombreNuevo) {
        LinkedHashMap<String, List<List<Object>>> pestanas = sheet(sheetId);
        if (!pestanas.containsKey(nombreActual)) {
            throw AccesoSheet.pestanaInexistente();
        }
        LinkedHashMap<String, List<List<Object>>> renombradas = new LinkedHashMap<>();
        pestanas.forEach((nombre, filas) -> renombradas.put(nombre.equals(nombreActual) ? nombreNuevo : nombre, filas));
        pestanas.clear();
        pestanas.putAll(renombradas);
    }

    @Override
    public synchronized void crearPestana(String sheetId, String pestana) {
        sheet(sheetId).putIfAbsent(pestana, new ArrayList<>());
    }

    @Override
    public synchronized List<String> leerEncabezados(String sheetId, String pestana) {
        List<List<Object>> filas = sheet(sheetId).getOrDefault(pestana, List.of());
        return filas.isEmpty()
                ? List.of()
                : filas.get(0).stream().map(String::valueOf).toList();
    }

    @Override
    public synchronized void escribirEncabezados(String sheetId, String pestana, List<String> encabezados) {
        List<List<Object>> filas = sheet(sheetId).computeIfAbsent(pestana, nombre -> new ArrayList<>());
        List<Object> fila = new ArrayList<>(encabezados);
        if (filas.isEmpty()) {
            filas.add(fila);
        } else {
            filas.set(0, fila);
        }
    }

    @Override
    public synchronized LinkedHashMap<String, List<String>> leerEncabezadosDeTodas(String sheetId) {
        LinkedHashMap<String, List<String>> encabezados = new LinkedHashMap<>();
        for (String pestana : sheet(sheetId).keySet()) {
            encabezados.put(pestana, leerEncabezados(sheetId, pestana));
        }
        return encabezados;
    }

    @Override
    public synchronized List<List<Object>> leerValores(String sheetId, String pestana) {
        List<List<Object>> filas = sheet(sheetId).get(pestana);
        if (filas == null) {
            throw AccesoSheet.pestanaInexistente();
        }
        return copia(filas);
    }

    @Override
    public synchronized void crearPestanaConValores(String sheetId, String pestana, List<List<Object>> filas) {
        LinkedHashMap<String, List<List<Object>>> pestanas = sheet(sheetId);
        if (pestanas.containsKey(pestana)) {
            // Google rechaza crear una pestaña con un nombre que ya existe.
            throw AccesoSheet.errorInesperado();
        }
        pestanas.put(pestana, copia(filas));
    }

    private static List<List<Object>> copia(List<List<Object>> filas) {
        List<List<Object>> copia = new ArrayList<>();
        for (List<Object> fila : filas) {
            copia.add(new ArrayList<>(fila));
        }
        return copia;
    }

    private LinkedHashMap<String, List<List<Object>>> sheet(String sheetId) {
        LinkedHashMap<String, List<List<Object>>> pestanas = sheets.get(sheetId);
        if (pestanas == null) {
            throw AccesoSheet.sinAcceso();
        }
        return pestanas;
    }
}
