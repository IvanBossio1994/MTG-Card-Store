package com.tcg.bot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * La app corre en la PC de la tienda y la usa el navegador de esa PC. Este filtro evita que una página web
 * cualquiera abierta en el mismo navegador mande formularios a la app (vender, cambiar la configuración,
 * desconectar Google) o la lea con un dominio que apunte a 127.0.0.1.
 * <p>
 * Con la segunda caja prendida ({@link LanAccess}), también entran pedidos de un celular de la red local por otro
 * puerto: esos tienen que venir a una IP privada, con la sesión que da el PIN, y solo pueden buscar, vender y
 * manejar reservas (nada de configuración, cuenta de Google, caja ni licencia).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    /** Lo que se puede abrir desde el celular sin PIN. */
    private static final List<String> LAN_PUBLIC = List.of("/celular", "/css/", "/js/", "/images/", "/favicon.ico");
    /** Lo que se puede usar desde el celular con PIN: buscar, vender y reservas. */
    private static final List<Pattern> LAN_ALLOWED = Stream.of(
            "/", "/juegos/[a-z]+", "/juegos/[a-z]+/stock", "/api/cartas/sugerencias", "/api/credito", "/imagen",
            "/configuracion/logo", "/ventas", "/inventory/quantity", "/reservas", "/reservas/[^/]+/(retirar|cancelar)",
            "/error"
    ).map(Pattern::compile).toList();

    private final LanAccess lan;

    @org.springframework.beans.factory.annotation.Autowired
    public LocalOnlyFilter(LanAccess lan) {
        this.lan = lan;
    }

    /** Sin segunda caja (tests). */
    LocalOnlyFilter() {
        this(null);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (lan != null && request.getLocalPort() == lan.getPort()) {
            filterLan(request, response, chain);
            return;
        }
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

    private void filterLan(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String host = request.getServerName();
        if (!lan.isEnabled() || !LanAccess.isPrivate(host)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String method = request.getMethod();
        boolean changesData = !"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method);
        if (changesData && !sameOrigin(request.getHeader("Origin"), host, request.getServerPort())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (LAN_PUBLIC.stream().anyMatch(prefix -> path.equals(prefix) || (prefix.endsWith("/") && path.startsWith(prefix)))) {
            chain.doFilter(request, response);
            return;
        }
        if (!lan.isValidSession(sessionOf(request))) {
            if ("GET".equals(method)) {
                response.sendRedirect(request.getContextPath() + "/celular");
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            }
            return;
        }
        if (LAN_ALLOWED.stream().noneMatch(pattern -> pattern.matcher(path).matches())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        request.setAttribute(REMOTE_ATTRIBUTE, Boolean.TRUE);
        chain.doFilter(request, response);
    }

    /** Lo marca el filtro en los pedidos que vienen del celular, para mostrar un menú corto. */
    public static final String REMOTE_ATTRIBUTE = "tcg.remoteRegister";

    private static String sessionOf(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (LanAccess.COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** Sin Origin se deja pasar (como en la PC); con Origin tiene que ser la misma dirección y puerto. */
    static boolean sameOrigin(String origin, String host, int port) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        try {
            URI uri = URI.create(origin.trim());
            int originPort = uri.getPort() == -1 ? 80 : uri.getPort();
            return host.equalsIgnoreCase(uri.getHost()) && originPort == port;
        } catch (IllegalArgumentException e) {
            return false;
        }
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
