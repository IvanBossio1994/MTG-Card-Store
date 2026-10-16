package com.tcg.bot.compartido;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Error de negocio con código HTTP y mensaje en español para el usuario.
 * Los servicios v2 la lanzan y {@link ManejadorErroresApi} la convierte en un ProblemDetail.
 * Ejemplo: {@code throw new ErrorDeNegocioException(HttpStatus.NOT_FOUND, "Reserva no encontrada", "No existe la reserva 12.")}.
 */
public class ErrorDeNegocioException extends RuntimeException {

    private final HttpStatus estado;
    private final String titulo;
    private final Map<String, Object> propiedades;

    /**
     * Crea el error con su código HTTP, un título corto y el detalle para el usuario.
     * Ejemplo: {@code new ErrorDeNegocioException(HttpStatus.CONFLICT, "Sin stock", "No queda stock de Sol Ring.")}.
     */
    public ErrorDeNegocioException(HttpStatus estado, String titulo, String detalle) {
        this(estado, titulo, detalle, Map.of());
    }

    /**
     * Igual que el anterior, con datos extra que viajan como propiedades del ProblemDetail.
     * Ejemplo: {@code new ErrorDeNegocioException(UNAUTHORIZED, "Sesión vencida", "La sesión...", Map.of("accion", "volver-a-iniciar-sesion"))}.
     */
    public ErrorDeNegocioException(HttpStatus estado, String titulo, String detalle, Map<String, Object> propiedades) {
        super(detalle);
        this.estado = estado;
        this.titulo = titulo;
        // Admite valores null (por ejemplo "campo": null en los errores de bloque).
        this.propiedades = Collections.unmodifiableMap(new LinkedHashMap<>(propiedades));
    }

    /**
     * Error 400 de un solo campo, con la misma forma que la validación con {@code @Valid}.
     * Ejemplo: {@code campoInvalido("archivo", "El logo no debe superar los 2 MB.")} → 400 {@code errores: {"archivo": "El logo no debe superar los 2 MB."}}.
     */
    public static ErrorDeNegocioException campoInvalido(String campo, String mensaje) {
        Map<String, String> errores = new LinkedHashMap<>();
        errores.put(campo, mensaje);
        return new ErrorDeNegocioException(
                HttpStatus.BAD_REQUEST,
                ManejadorErroresApi.TITULO_DATOS_INVALIDOS,
                ManejadorErroresApi.DETALLE_DATOS_INVALIDOS,
                Map.of("errores", errores));
    }

    /**
     * Error 400 de validación con {@code errores} (campo del JSON → mensaje) más el campo de la pantalla y un código fijo,
     * para que el front marque el campo sin comparar textos.
     * Ejemplo: {@code datosInvalidos({"nombreNuevo": "Ya existe una pestaña con ese nombre."}, "nombre-nuevo", "NOMBRE_REPETIDO")}
     * → 400 con {@code errores}, {@code campo: "nombre-nuevo"} y {@code codigo: "NOMBRE_REPETIDO"}.
     */
    public static ErrorDeNegocioException datosInvalidos(
            Map<String, String> errores, String campoPantalla, String codigo) {
        Map<String, Object> propiedades = new LinkedHashMap<>();
        propiedades.put("errores", new LinkedHashMap<>(errores));
        propiedades.put("campo", campoPantalla);
        propiedades.put("codigo", codigo);
        return new ErrorDeNegocioException(
                HttpStatus.BAD_REQUEST,
                ManejadorErroresApi.TITULO_DATOS_INVALIDOS,
                ManejadorErroresApi.DETALLE_DATOS_INVALIDOS,
                propiedades);
    }

    /**
     * Error de bloque (no de un campo) con código fijo y {@code campo: null}.
     * Ejemplo: {@code deBloque(NOT_FOUND, "Pestaña inexistente", "La pestaña ya no existe en el Sheet. Se actualizó la lista.", "PESTANA_INEXISTENTE")}
     * → 404 con {@code campo: null} y {@code codigo: "PESTANA_INEXISTENTE"}.
     */
    public static ErrorDeNegocioException deBloque(HttpStatus estado, String titulo, String detalle, String codigo) {
        Map<String, Object> propiedades = new LinkedHashMap<>();
        propiedades.put("campo", null);
        propiedades.put("codigo", codigo);
        return new ErrorDeNegocioException(estado, titulo, detalle, propiedades);
    }

    /**
     * Código HTTP del error. Ejemplo: {@code HttpStatus.CONFLICT}.
     */
    public HttpStatus getEstado() {
        return estado;
    }

    /**
     * Título corto en español. Ejemplo: {@code "Sin stock"}.
     */
    public String getTitulo() {
        return titulo;
    }

    /**
     * Datos extra para el ProblemDetail (vacío si no hay). Ejemplo: {@code {"cuentaServicio": "tcgbotsheet@..."}}.
     */
    public Map<String, Object> getPropiedades() {
        return propiedades;
    }
}
