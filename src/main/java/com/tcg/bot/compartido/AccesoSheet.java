package com.tcg.bot.compartido;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Base común de acceso al Google Sheet de la tienda (v2). La usan todos los módulos por composición.
 * Implementaciones: {@code google.AccesoSheetCuentaServicio} (real, con la cuenta de servicio) y
 * {@link AccesoSheetEnMemoria} (tests y modo prueba). Cada llamada lee o escribe en el Sheet en el momento.
 * <p>
 * Errores: si la cuenta de servicio no tiene acceso lanza {@link #sinAcceso()} (403); si no hay conexión, {@link #sinConexion()} (503).
 */
public interface AccesoSheet {

    /** Mail de la cuenta de servicio con la que hay que compartir cada Sheet como editor. */
    String CUENTA_SERVICIO = "tcgbotsheet@tcgstorehelper.iam.gserviceaccount.com";

    /**
     * Devuelve los nombres de las pestañas del Sheet, en el orden en que aparecen.
     * Ejemplo: Sheet con "Hoja 1" y "Ventas" → {@code ["Hoja 1", "Ventas"]}; Sheet no compartido → 403.
     */
    List<String> listarPestanas(String sheetId);

    /**
     * Cambia el nombre de una pestaña existente.
     * Ejemplo: {@code renombrarPestana(id, "Hoja 1", "Inventario")} → la pestaña pasa a llamarse "Inventario";
     * si "Hoja 1" ya no existe → {@link #pestanaInexistente()} (404).
     */
    void renombrarPestana(String sheetId, String nombreActual, String nombreNuevo);

    /**
     * Agrega una pestaña vacía al final del Sheet.
     * Ejemplo: {@code crearPestana(id, "Caja")} → el Sheet tiene una pestaña "Caja" nueva.
     */
    void crearPestana(String sheetId, String pestana);

    /**
     * Lee la fila 1 (encabezados) de una pestaña. Vacía si la fila no tiene datos.
     * Ejemplo: pestaña "Caja" recién creada → {@code []}.
     */
    List<String> leerEncabezados(String sheetId, String pestana);

    /**
     * Escribe los encabezados en la fila 1 de una pestaña, desde la columna A.
     * Ejemplo: {@code escribirEncabezados(id, "Clientes", ["Nombre", "Apellido"])} → A1="Nombre", B1="Apellido".
     */
    void escribirEncabezados(String sheetId, String pestana, List<String> encabezados);

    /**
     * Lee la fila 1 de todas las pestañas, en el orden del Sheet (en el real: una llamada para la lista y un solo
     * {@code batchGet} para las filas). Una pestaña sin encabezados viene con la lista vacía.
     * Ejemplo: Sheet con "Inventario" (Cantidad, Nombre) y "Caja" vacía → {@code {"Inventario": ["Cantidad", "Nombre"], "Caja": []}}.
     */
    LinkedHashMap<String, List<String>> leerEncabezadosDeTodas(String sheetId);

    /**
     * Lee todos los valores de una pestaña (no las fórmulas: su resultado). Cada celda es un {@code String}, un
     * {@code Number} o un {@code Boolean}; las fechas vienen como texto, como se ven. Las filas pueden tener largos distintos.
     * Ejemplo: pestaña con encabezados y una carta → {@code [["Cantidad", "Nombre"], [4, "Sol Ring"]]}; pestaña vacía → {@code []}.
     */
    List<List<Object>> leerValores(String sheetId, String pestana);

    /**
     * Crea una pestaña nueva al final y le escribe los valores desde A1, en una sola llamada (todo o nada).
     * Ejemplo: {@code crearPestanaConValores(id, "Stock 2026", [["Cantidad", "Nombre"], [4, "Sol Ring"]])} → pestaña nueva con 2 filas.
     */
    void crearPestanaConValores(String sheetId, String pestana, List<List<Object>> filas);

    /**
     * Crea la pestaña si no está en {@code pestanasActuales} y escribe los encabezados solo si la fila 1 está vacía
     * (nunca pisa encabezados existentes, porque el Sheet también se edita a mano). Devuelve si tuvo que crearla.
     * Ejemplo: Sheet sin "Caja" → crea "Caja" con sus encabezados y devuelve {@code true}.
     */
    default boolean asegurarPestana(
            String sheetId, List<String> pestanasActuales, String pestana, List<String> encabezados) {
        boolean creada = !pestanasActuales.contains(pestana);
        if (creada) {
            crearPestana(sheetId, pestana);
        }
        if (leerEncabezados(sheetId, pestana).stream().allMatch(String::isBlank)) {
            escribirEncabezados(sheetId, pestana, encabezados);
        }
        return creada;
    }

    /**
     * Error 403: el Sheet no está compartido con la cuenta de servicio (o no existe).
     * Ejemplo: {@code throw AccesoSheet.sinAcceso()} → 403 "La app no tiene acceso a este Sheet. Compartirlo con … como editor."
     */
    static ErrorDeNegocioException sinAcceso() {
        return new ErrorDeNegocioException(
                HttpStatus.FORBIDDEN,
                "Sin acceso al Sheet",
                "La app no tiene acceso a este Sheet. Compartirlo con " + CUENTA_SERVICIO + " como editor.",
                Map.of("cuentaServicio", CUENTA_SERVICIO));
    }

    /**
     * Error 503: no se pudo hablar con Google.
     * Ejemplo: {@code throw AccesoSheet.sinConexion()} → 503 "No hay conexión a internet. Se puede reintentar cuando se restablezca."
     */
    static ErrorDeNegocioException sinConexion() {
        return new ErrorDeNegocioException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Sin conexión",
                "No hay conexión a internet. Se puede reintentar cuando se restablezca.");
    }

    /**
     * Error 404: la pestaña pedida ya no está en el Sheet (la borraron o renombraron a mano).
     * Ejemplo: {@code throw AccesoSheet.pestanaInexistente()} → 404 "La pestaña ya no existe en el Sheet. Se actualizó la lista."
     * con {@code codigo: "PESTANA_INEXISTENTE"}.
     */
    static ErrorDeNegocioException pestanaInexistente() {
        return ErrorDeNegocioException.deBloque(
                HttpStatus.NOT_FOUND,
                "Pestaña inexistente",
                "La pestaña ya no existe en el Sheet. Se actualizó la lista.",
                "PESTANA_INEXISTENTE");
    }

    /**
     * Error 500 inesperado (Google respondió algo raro o falta la clave de la cuenta de servicio).
     * Ejemplo: {@code throw AccesoSheet.errorInesperado()} → 500 "No se pudo guardar por un error inesperado. Se puede reintentar en un momento."
     */
    static ErrorDeNegocioException errorInesperado() {
        return new ErrorDeNegocioException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno",
                "No se pudo guardar por un error inesperado. Se puede reintentar en un momento.");
    }
}
