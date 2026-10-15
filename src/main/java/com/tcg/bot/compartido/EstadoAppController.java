package com.tcg.bot.compartido;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estado de la app v2: si arrancó bien, con qué versión y en qué modo (prueba o normal). Contrato: docs/v2/api/compartido.md.
 * No tiene service porque no hay reglas de negocio: solo lee la versión y el modo.
 */
@RestController
@RequestMapping("/api/v1/estado-app")
@Tag(name = "Estado de la app", description = "Si la app arrancó bien, con su versión y modo")
public class EstadoAppController {

    static final String VERSION_DESCONOCIDA = "desconocida";
    static final String MODO_PRUEBA = "prueba";
    static final String MODO_NORMAL = "normal";

    private final Environment environment;
    private final String version;

    /**
     * Crea el controller con el entorno de Spring y la versión del build.
     * Ejemplo: {@code new EstadoAppController(env, "0.0.1-SNAPSHOT")}.
     */
    public EstadoAppController(Environment environment, @Value("${app.version:}") String version) {
        this.environment = environment;
        this.version = version;
    }

    /**
     * Responde que la app está levantada, con la versión y el modo.
     * Ejemplo: GET /api/v1/estado-app en modo prueba → 200 {@code {"estado":"ok","version":"0.0.1-SNAPSHOT","modo":"prueba"}}.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consultar el estado de la app",
            description = "Devuelve \"ok\" si la app está levantada, la versión del build y el modo: \"prueba\" (sin Google, datos en memoria) o \"normal\" (con el Sheet real).")
    @ApiResponse(responseCode = "200", description = "La app está levantada",
            content = @Content(schema = @Schema(implementation = EstadoAppRespuesta.class),
                    examples = @ExampleObject(value = "{\"estado\":\"ok\",\"version\":\"0.0.1-SNAPSHOT\",\"modo\":\"prueba\"}")))
    public EstadoAppRespuesta consultarEstadoApp() {
        String modo = environment.matchesProfiles("e2e") ? MODO_PRUEBA : MODO_NORMAL;
        return new EstadoAppRespuesta("ok", versionLegible(), modo);
    }

    private String versionLegible() {
        // Sin filtrado de Maven (por ejemplo, corriendo desde el IDE) queda el texto "@project.version@".
        if (version == null || version.isBlank() || version.startsWith("@")) {
            return VERSION_DESCONOCIDA;
        }
        return version;
    }

    /**
     * Respuesta de GET /api/v1/estado-app. Va acá adentro porque solo la usa este endpoint.
     * Ejemplo: {@code new EstadoAppRespuesta("ok", "0.0.1-SNAPSHOT", "prueba")}.
     */
    @Schema(description = "Estado de la app")
    public record EstadoAppRespuesta(
            @Schema(description = "Siempre \"ok\" si la app responde", example = "ok")
            String estado,
            @Schema(description = "Versión del build", example = "0.0.1-SNAPSHOT")
            String version,
            @Schema(description = "\"prueba\": sin Google y con datos en memoria. \"normal\": con el Sheet real.", example = "prueba")
            String modo
    ) {
    }
}
