package com.tcg.bot.compartido;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Convierte los errores de la API v2 ({@code /api/**}) en {@code ProblemDetail} con mensajes en español.
 * Contrato: docs/v2/api/compartido.md.
 * <p>
 * Para rutas fuera de {@code /api/} (v1 en {@code /}, frontend en {@code /app}) vuelve a lanzar la misma excepción:
 * Spring sigue entonces con su manejo de siempre y la v1 no cambia.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ManejadorErroresApi {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresApi.class);
    private static final String PREFIJO_API = "/api/";

    /**
     * Ruta inexistente bajo /api → 404.
     * Ejemplo: GET /api/v1/no-existe → 404 "No existe la dirección pedida: GET /api/v1/no-existe."
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ProblemDetail> rutaInexistente(Exception ex, HttpServletRequest request) throws Exception {
        soloApi(ex, request);
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado",
                "No existe la dirección pedida: " + request.getMethod() + " " + request.getRequestURI() + ".", request);
    }

    /**
     * Método HTTP no soportado → 405.
     * Ejemplo: POST /api/v1/estado-app → 405 "La dirección /api/v1/estado-app no acepta el método POST."
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> metodoNoPermitido(HttpRequestMethodNotSupportedException ex,
                                                           HttpServletRequest request) throws Exception {
        soloApi(ex, request);
        ResponseEntity<ProblemDetail> respuesta = problema(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido",
                "La dirección " + request.getRequestURI() + " no acepta el método " + ex.getMethod() + ".", request);
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                    .allow(ex.getSupportedHttpMethods().toArray(new org.springframework.http.HttpMethod[0]))
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(respuesta.getBody());
        }
        return respuesta;
    }

    /**
     * Falla de validación de un DTO con {@code @Valid} → 400 con el mapa {@code errores} (campo → mensaje).
     * Ejemplo: {"nombre": ""} → 400 {@code errores: {"nombre": "no debe estar vacío"}}.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> datosInvalidos(MethodArgumentNotValidException ex,
                                                        HttpServletRequest request) throws Exception {
        soloApi(ex, request);
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ResponseEntity<ProblemDetail> respuesta =
                problema(HttpStatus.BAD_REQUEST, "Datos inválidos", "Hay datos para corregir.", request);
        respuesta.getBody().setProperty("errores", errores);
        return respuesta;
    }

    /**
     * Falla de validación de parámetros del método (por ejemplo {@code @Positive} en un path variable) → 400.
     * Ejemplo: GET /api/v1/reservas/-1 → 400 "Hay datos para corregir."
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> parametrosInvalidos(HandlerMethodValidationException ex,
                                                             HttpServletRequest request) throws Exception {
        soloApi(ex, request);
        return problema(HttpStatus.BAD_REQUEST, "Datos inválidos", "Hay datos para corregir.", request);
    }

    /**
     * JSON mal formado, parámetro faltante o de tipo incorrecto → 400.
     * Ejemplo: cuerpo "{nombre:" → 400 "Revisá los datos enviados: el pedido no se pudo leer."
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> pedidoInvalido(Exception ex, HttpServletRequest request) throws Exception {
        soloApi(ex, request);
        return problema(HttpStatus.BAD_REQUEST, "Pedido inválido",
                "Revisá los datos enviados: el pedido no se pudo leer.", request);
    }

    /**
     * Content-Type no soportado → 415.
     * Ejemplo: POST con text/plain → 415 "El formato del pedido no es válido. Enviá los datos como JSON."
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> formatoNoSoportado(HttpMediaTypeNotSupportedException ex,
                                                            HttpServletRequest request) throws Exception {
        soloApi(ex, request);
        return problema(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato no soportado",
                "El formato del pedido no es válido. Enviá los datos como JSON.", request);
    }

    /**
     * Error de negocio lanzado por un servicio v2 → su código y su mensaje.
     * Ejemplo: {@code ErrorDeNegocioException(CONFLICT, "Sin stock", "No queda stock.")} → 409 con ese detalle.
     */
    @ExceptionHandler(ErrorDeNegocioException.class)
    public ResponseEntity<ProblemDetail> errorDeNegocio(ErrorDeNegocioException ex,
                                                        HttpServletRequest request) throws Exception {
        soloApi(ex, request);
        return problema(ex.getEstado(), ex.getTitulo(), ex.getMessage(), request);
    }

    /**
     * {@code ResponseStatusException} → su código, con el motivo como detalle.
     * Ejemplo: {@code new ResponseStatusException(NOT_FOUND, "No existe la carta.")} → 404 "No existe la carta."
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> conEstado(ResponseStatusException ex,
                                                   HttpServletRequest request) throws Exception {
        soloApi(ex, request);
        HttpStatus estado = HttpStatus.resolve(ex.getStatusCode().value());
        if (estado == null) {
            estado = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String detalle = ex.getReason() != null ? ex.getReason() : "No se pudo completar el pedido.";
        return problema(estado, estado.is4xxClientError() ? "Pedido rechazado" : "Error interno", detalle, request);
    }

    /**
     * Cualquier otro error inesperado → 500 con un mensaje genérico (el detalle técnico va al log).
     * Ejemplo: NullPointerException en un servicio → 500 "Ocurrió un error inesperado. Probá de nuevo en unos minutos."
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> errorInesperado(Exception ex, HttpServletRequest request) throws Exception {
        soloApi(ex, request);
        log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado. Probá de nuevo en unos minutos.", request);
    }

    /** Si el pedido no es de la API, relanza la excepción original para que Spring siga como siempre. */
    private void soloApi(Exception ex, HttpServletRequest request) throws Exception {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        if (!ruta.startsWith(PREFIJO_API)) {
            throw ex;
        }
    }

    private ResponseEntity<ProblemDetail> problema(HttpStatus estado, String titulo, String detalle,
                                                   HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problema);
    }
}
