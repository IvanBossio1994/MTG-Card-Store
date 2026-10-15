package com.tcg.bot.compartido;

import org.springframework.http.HttpStatus;

/**
 * Error de negocio con código HTTP y mensaje en español para el usuario.
 * Los servicios v2 la lanzan y {@link ManejadorErroresApi} la convierte en un ProblemDetail.
 * Ejemplo: {@code throw new ErrorDeNegocioException(HttpStatus.NOT_FOUND, "Reserva no encontrada", "No existe la reserva 12.")}.
 */
public class ErrorDeNegocioException extends RuntimeException {

    private final HttpStatus estado;
    private final String titulo;

    /**
     * Crea el error con su código HTTP, un título corto y el detalle para el usuario.
     * Ejemplo: {@code new ErrorDeNegocioException(HttpStatus.CONFLICT, "Sin stock", "No queda stock de Sol Ring.")}.
     */
    public ErrorDeNegocioException(HttpStatus estado, String titulo, String detalle) {
        super(detalle);
        this.estado = estado;
        this.titulo = titulo;
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
}
