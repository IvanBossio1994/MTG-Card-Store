package com.tcg.bot.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import com.tcg.bot.TcgBotApplication;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/** CA7: con el perfil e2e no hay beans de v1 ni de Google (sin descarga de CK ni sincronización). CA4/CA5 de punta a punta. */
@SpringBootTest(classes = TcgBotApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
class ModoPruebaTest {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private TestRestTemplate http;

    @Test
    void noCargaBeansDeLegacyNiDeGoogle() {
        assertThat(contexto.containsBeanDefinition("dashboardController")).isFalse();
        assertThat(contexto.containsBeanDefinition("cardKingdomApiService")).isFalse();
        assertThat(contexto.containsBeanDefinition("googleOAuthService")).isFalse();
        assertThat(Arrays.stream(contexto.getBeanDefinitionNames())
                        .map(nombre -> contexto.getType(nombre))
                        .filter(tipo -> tipo != null)
                        .map(Class::getName))
                .noneMatch(clase -> clase.startsWith("com.tcg.bot.legacy.") || clase.startsWith("com.tcg.bot.google."));
    }

    @Test
    void estadoAppInformaModoPrueba() {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> respuesta = http.getForEntity("/api/v1/estado-app", Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).containsEntry("estado", "ok").containsEntry("modo", "prueba");
    }

    @Test
    @SuppressWarnings("unchecked")
    void configuracionFuncionaSinGoogleConDatosDePrueba() {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> respuesta = http.getForEntity("/api/v1/configuracion", Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Map<String, Object>) respuesta.getBody().get("sheet"))
                .containsEntry("gmail", ModoPruebaConfig.GMAIL_DE_PRUEBA);
    }

    @Test
    void rutaInexistenteDevuelveProblemDetailEnEspanol() {
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> respuesta = http.getForEntity("/api/v1/no-existe", Map.class);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(respuesta.getBody())
                .containsEntry("detail", "No existe la dirección pedida: GET /api/v1/no-existe.");
    }

    @Test
    void swaggerDocumentaEstadoAppYNoLaV1() {
        String apiDocs = http.getForObject("/v3/api-docs", String.class);

        assertThat(apiDocs).contains("/api/v1/estado-app").contains("\"modo\":\"prueba\"");
        assertThat(http.getForEntity("/swagger-ui", String.class)
                        .getStatusCode()
                        .is2xxSuccessful())
                .isTrue();
    }
}
