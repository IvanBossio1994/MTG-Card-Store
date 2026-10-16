package com.tcg.bot.compartido;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Sirve el frontend v2 (build de Vite en {@code classpath:/static/app/}) en {@code /app}.
 * Las rutas que no son un archivo existente devuelven {@code index.html}, así React resuelve la navegación.
 * Si el frontend no está compilado, {@code /app} responde 404.
 */
@Configuration
public class FrontendConfig implements WebMvcConfigurer {

    static final String UBICACION_FRONTEND = "classpath:/static/app/";
    static final String INDEX = "index.html";

    /**
     * Registra {@code /app/**} con fallback a {@code index.html}.
     * Ejemplo: GET /app/configuracion → index.html · GET /app/assets/index-abc.js → ese archivo.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/app/**")
                .addResourceLocations(UBICACION_FRONTEND)
                .resourceChain(true)
                .addResolver(new ResolverConFallbackAIndex());
    }

    /**
     * Hace que {@code /app} y {@code /app/} muestren el frontend (Spring ignora la ruta vacía en los recursos).
     * Ejemplo: GET /app → mismo contenido que GET /app/index.html.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/app").setViewName("forward:/app/" + INDEX);
        registry.addViewController("/app/").setViewName("forward:/app/" + INDEX);
    }

    /** Busca el archivo pedido; si no existe (o es una carpeta) y no es un archivo con extensión, devuelve index.html. */
    static class ResolverConFallbackAIndex extends PathResourceResolver {

        @Override
        protected Resource getResource(String rutaRecurso, Resource ubicacion) throws IOException {
            Resource pedido = rutaRecurso.isEmpty() ? null : ubicacion.createRelative(rutaRecurso);
            if (pedido != null && pedido.exists() && pedido.isReadable() && esArchivo(pedido)) {
                return pedido;
            }
            if (pareceArchivo(rutaRecurso)) {
                // Un .js/.css/.png que no existe es un 404 real, no una ruta de React.
                return null;
            }
            Resource index = ubicacion.createRelative(INDEX);
            return index.exists() && index.isReadable() ? index : null;
        }

        private static boolean esArchivo(Resource recurso) {
            try {
                return !recurso.getFile().isDirectory();
            } catch (IOException e) {
                // Dentro del .jar no hay File: si es legible y no termina en "/", es un archivo.
                String url = recurso.getDescription();
                return url != null && !url.endsWith("/]");
            }
        }

        private static boolean pareceArchivo(String rutaRecurso) {
            int ultimaBarra = rutaRecurso.lastIndexOf('/');
            return rutaRecurso.substring(ultimaBarra + 1).contains(".");
        }
    }
}
