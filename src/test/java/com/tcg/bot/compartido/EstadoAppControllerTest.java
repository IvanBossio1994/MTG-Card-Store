package com.tcg.bot.compartido;

import com.tcg.bot.compartido.EstadoAppController.EstadoAppRespuesta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CA4: GET /api/v1/estado-app (API y lógica de versión y modo). */
@WebMvcTest(EstadoAppController.class)
class EstadoAppControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void estadoAppResponde200ConEstadoVersionYPerfil() throws Exception {
        mockMvc.perform(get("/api/v1/estado-app"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.estado").value("ok"))
                .andExpect(jsonPath("$.version").value(not(emptyString())))
                .andExpect(jsonPath("$.modo").value("normal"));
    }

    @Test
    void estadoAppConOtroMetodoResponde405EnEspanol() throws Exception {
        mockMvc.perform(post("/api/v1/estado-app"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.title").value("Método no permitido"))
                .andExpect(jsonPath("$.detail").value("La dirección /api/v1/estado-app no acepta el método POST."))
                .andExpect(jsonPath("$.instance").value("/api/v1/estado-app"));
    }

    @Test
    void enModoPruebaDevuelveOkConVersionYModoPrueba() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("e2e");

        assertThat(new EstadoAppController(env, "1.2.3").consultarEstadoApp())
                .isEqualTo(new EstadoAppRespuesta("ok", "1.2.3", "prueba"));
    }

    @Test
    void sinModoPruebaDevuelveModoNormal() {
        assertThat(new EstadoAppController(new MockEnvironment(), "1.0").consultarEstadoApp().modo()).isEqualTo("normal");
    }

    @Test
    void versionSinFiltrarOVaciaSeMuestraComoDesconocida() {
        assertThat(new EstadoAppController(new MockEnvironment(), "@project.version@").consultarEstadoApp().version())
                .isEqualTo(EstadoAppController.VERSION_DESCONOCIDA);
        assertThat(new EstadoAppController(new MockEnvironment(), "").consultarEstadoApp().version())
                .isEqualTo(EstadoAppController.VERSION_DESCONOCIDA);
    }
}
