package com.tcg.bot.compartido;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CA5: errores de la API como ProblemDetail en español. Fuera de /api no cambia nada. */
@WebMvcTest(ManejadorErroresApiTest.ControladorDePrueba.class)
@Import(ManejadorErroresApiTest.ControladorDePrueba.class)
class ManejadorErroresApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rutaInexistenteBajoApiResponde404ProblemDetailEnEspanol() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("No existe la dirección pedida: GET /api/v1/no-existe."))
                .andExpect(jsonPath("$.instance").value("/api/v1/no-existe"));
    }

    @Test
    void rutaInexistenteFueraDeApiNoDevuelveProblemDetail() throws Exception {
        mockMvc.perform(get("/pantalla-que-no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void dtoInvalidoResponde400ConErroresPorCampo() throws Exception {
        mockMvc.perform(post("/api/v1/prueba").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"cantidad\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Datos inválidos"))
                .andExpect(jsonPath("$.detail").value("Hay datos para corregir."))
                .andExpect(jsonPath("$.errores.nombre").value("Ingresá un nombre."))
                .andExpect(jsonPath("$.errores.cantidad").value("La cantidad tiene que ser mayor a 0."));
    }

    @Test
    void jsonMalFormadoResponde400() throws Exception {
        mockMvc.perform(post("/api/v1/prueba").contentType(MediaType.APPLICATION_JSON).content("{nombre:"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Pedido inválido"))
                .andExpect(jsonPath("$.detail").value("El pedido no se pudo leer. Los datos enviados no tienen el formato esperado."));
    }

    @Test
    void formatoNoSoportadoResponde415() throws Exception {
        mockMvc.perform(post("/api/v1/prueba").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").value("Formato no soportado"));
    }

    @Test
    void errorDeNegocioUsaSuCodigoYMensaje() throws Exception {
        mockMvc.perform(get("/api/v1/prueba/conflicto"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Sin stock"))
                .andExpect(jsonPath("$.detail").value("No queda stock de Sol Ring."));
    }

    @Test
    void errorInesperadoResponde500SinDetalleTecnico() throws Exception {
        mockMvc.perform(get("/api/v1/prueba/falla"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Error interno"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error inesperado. Se puede reintentar en un momento."));
    }

    record PedidoDePrueba(
            @NotBlank(message = "Ingresá un nombre.") String nombre,
            @Positive(message = "La cantidad tiene que ser mayor a 0.") int cantidad
    ) {
    }

    @RestController
    static class ControladorDePrueba {

        @PostMapping("/api/v1/prueba")
        PedidoDePrueba crear(@Valid @RequestBody PedidoDePrueba pedido) {
            return pedido;
        }

        @GetMapping("/api/v1/prueba/conflicto")
        String conflicto() {
            throw new ErrorDeNegocioException(HttpStatus.CONFLICT, "Sin stock", "No queda stock de Sol Ring.");
        }

        @GetMapping("/api/v1/prueba/falla")
        String falla() {
            throw new IllegalStateException("detalle técnico interno");
        }
    }
}
