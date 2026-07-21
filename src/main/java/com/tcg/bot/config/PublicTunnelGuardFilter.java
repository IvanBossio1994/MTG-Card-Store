package com.tcg.bot.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "app.public-tunnel-guard.enabled", havingValue = "true")
public class PublicTunnelGuardFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isPublicHost(request) && !isPublicAllowedPath(request.getRequestURI())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicHost(HttpServletRequest request) {
        String host = request.getHeader("Host");
        if (host == null || host.isBlank()) {
            return false;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return !normalizedHost.startsWith("127.0.0.1")
                && !normalizedHost.startsWith("localhost");
    }

    private boolean isPublicAllowedPath(String path) {
        return path != null && path.startsWith("/webhooks/whatsapp");
    }
}
