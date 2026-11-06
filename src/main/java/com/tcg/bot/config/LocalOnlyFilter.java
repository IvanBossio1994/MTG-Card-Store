package com.tcg.bot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * La app corre en la PC de la tienda y solo la usa el navegador de esa PC. Este filtro evita que una página web
 * cualquiera abierta en el mismo navegador mande formularios a la app (vender, cambiar la configuración,
 * desconectar Google) o la lea con un dominio que apunte a 127.0.0.1.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isLocalHost(request.getServerName())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String method = request.getMethod();
        boolean changesData = !"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method);
        if (changesData && !isLocalOrigin(request.getHeader("Origin"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        chain.doFilter(request, response);
    }

    static boolean isLocalHost(String host) {
        return host != null && LOCAL_HOSTS.contains(host.toLowerCase(Locale.ROOT));
    }

    /** Sin Origin (herramientas, navegadores viejos) se deja pasar; con Origin tiene que ser esta misma PC. */
    static boolean isLocalOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        try {
            return isLocalHost(URI.create(origin.trim()).getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
