package com.tcg.bot.compartido;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sheets simulados en memoria, para los tests y el modo prueba (perfil {@code e2e}). No usa Google ni internet.
 * Solo se puede entrar a los Sheets "compartidos" con {@link #compartir}; cualquier otro ID responde 403, como un Sheet
 * no compartido con la cuenta de servicio.
 */
public class AccesoSheetEnMemoria implements AccesoSheet {

    private final Map<String, LinkedHashMap<String, List<String>>> sheets = new ConcurrentHashMap<>();

    /**
     * Da de alta un Sheet compartido con la cuenta de servicio, con sus pestañas vacías (sin encabezados).
     * Ejemplo: {@code compartir("1Abc…", "Hoja 1", "Ventas")} → {@code listarPestanas("1Abc…")} devuelve esas dos.
     */
    public AccesoSheetEnMemoria compartir(String sheetId, String... pestanas) {
        LinkedHashMap<String, List<String>> nuevas = new LinkedHashMap<>();
        for (String pestana : pestanas) {
            nuevas.put(pestana, List.of());
        }
        sheets.put(sheetId, nuevas);
        return this;
    }

    @Override
    public synchronized List<String> listarPestanas(String sheetId) {
        return List.copyOf(sheet(sheetId).keySet());
    }

    @Override
    public synchronized void renombrarPestana(String sheetId, String nombreActual, String nombreNuevo) {
        LinkedHashMap<String, List<String>> pestanas = sheet(sheetId);
        LinkedHashMap<String, List<String>> renombradas = new LinkedHashMap<>();
        pestanas.forEach((nombre, encabezados) ->
                renombradas.put(nombre.equals(nombreActual) ? nombreNuevo : nombre, encabezados));
        pestanas.clear();
        pestanas.putAll(renombradas);
    }

    @Override
    public synchronized void crearPestana(String sheetId, String pestana) {
        sheet(sheetId).putIfAbsent(pestana, List.of());
    }

    @Override
    public synchronized List<String> leerEncabezados(String sheetId, String pestana) {
        return sheet(sheetId).getOrDefault(pestana, List.of());
    }

    @Override
    public synchronized void escribirEncabezados(String sheetId, String pestana, List<String> encabezados) {
        sheet(sheetId).put(pestana, new ArrayList<>(encabezados));
    }

    private LinkedHashMap<String, List<String>> sheet(String sheetId) {
        LinkedHashMap<String, List<String>> pestanas = sheets.get(sheetId);
        if (pestanas == null) {
            throw AccesoSheet.sinAcceso();
        }
        return pestanas;
    }
}
