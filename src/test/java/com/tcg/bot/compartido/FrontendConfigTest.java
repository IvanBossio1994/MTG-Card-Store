package com.tcg.bot.compartido;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

/** /app sirve archivos existentes y cae a index.html en las rutas de React. */
class FrontendConfigTest {

    @TempDir
    Path carpeta;

    private Resource resolver(String ruta) throws Exception {
        Resource ubicacion = new FileSystemResource(carpeta.toString() + "/");
        return new ProbadorResolver().resolver(ruta, ubicacion);
    }

    @Test
    void archivoExistenteSeDevuelveTalCual() throws Exception {
        Files.writeString(carpeta.resolve("index.html"), "<html></html>");
        Files.createDirectories(carpeta.resolve("assets"));
        Files.writeString(carpeta.resolve("assets/app.js"), "console.log(1)");

        assertThat(resolver("assets/app.js").getFilename()).isEqualTo("app.js");
    }

    @Test
    void rutaDeReactOCarpetaDevuelveIndex() throws Exception {
        Files.writeString(carpeta.resolve("index.html"), "<html></html>");
        Files.createDirectories(carpeta.resolve("assets"));

        assertThat(resolver("configuracion").getFilename()).isEqualTo("index.html");
        assertThat(resolver("reservas/12").getFilename()).isEqualTo("index.html");
        assertThat(resolver("assets").getFilename()).isEqualTo("index.html");
    }

    @Test
    void archivoConExtensionQueNoExisteEs404() throws Exception {
        Files.writeString(carpeta.resolve("index.html"), "<html></html>");

        assertThat(resolver("assets/no-existe.js")).isNull();
    }

    @Test
    void sinFrontendCompiladoNoHayIndex() throws Exception {
        assertThat(resolver("configuracion")).isNull();
    }

    /** Expone el método protegido del resolver para probarlo. */
    static class ProbadorResolver extends FrontendConfig.ResolverConFallbackAIndex {
        Resource resolver(String ruta, Resource ubicacion) throws Exception {
            return getResource(ruta, ubicacion);
        }
    }
}
