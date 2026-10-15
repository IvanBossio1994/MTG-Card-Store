package com.tcg.bot.compartido;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

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
     * Ejemplo: {@code new ErrorDeNegocioException(UNAUTHORIZED, "Sesión vencida", "Tu sesión...", Map.of("accion", "volver-a-iniciar-sesion"))}.
     */
    public ErrorDeNegocioException(HttpStatus estado, String titulo, String detalle, Map<String, Object> propiedades) {
        super(detalle);
        this.estado = estado;
        this.titulo = titulo;
        this.propiedades = Map.copyOf(propiedades);
    }

    /**
     * Error 400 de un solo campo, con la misma forma que la validación con {@code @Valid}.
     * Ejemplo: {@code campoInvalido("archivo", "El logo pesa más de 2 MB.")} → 400 {@code errores: {"archivo": "El logo pesa más de 2 MB."}}.
     */
    public static ErrorDeNegocioException campoInvalido(String campo, String mensaje) {
        Map<String, String> errores = new LinkedHashMap<>();
        errores.put(campo, mensaje);
        return new ErrorDeNegocioException(HttpStatus.BAD_REQUEST, ManejadorErroresApi.TITULO_DATOS_INVALIDOS,
                ManejadorErroresApi.DETALLE_DATOS_INVALIDOS, Map.of("errores", errores));
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
