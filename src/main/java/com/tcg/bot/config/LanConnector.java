package com.tcg.bot.config;

import org.apache.catalina.LifecycleState;
import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Abre o cierra el puerto de la red local (para el celular) sin reiniciar la app. La PC sigue usando su puerto
 * de siempre, que solo escucha en 127.0.0.1.
 */
@Component
public class LanConnector {

    private static final Logger log = LoggerFactory.getLogger(LanConnector.class);

    private final LanAccess lan;
    private final org.springframework.context.ApplicationContext context;
    private Connector connector;

    public LanConnector(LanAccess lan, org.springframework.context.ApplicationContext context) {
        this.lan = lan;
        this.context = context;
    }

    @EventListener
    public void onStart(ServletWebServerInitializedEvent event) {
        if (lan.isEnabled()) {
            try {
                open();
            } catch (Exception e) {
                log.warn("No se pudo abrir la caja del celular en el puerto {}: {}", lan.getPort(), e.getMessage());
            }
        }
    }

    public synchronized boolean isOpen() {
        return connector != null;
    }

    /** Empieza a escuchar en la red local. Falla si el puerto está ocupado. */
    public synchronized void open() throws Exception {
        TomcatWebServer server = tomcat();
        if (connector != null || server == null) {
            return;
        }
        Connector created = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        created.setPort(lan.getPort());
        created.setProperty("address", "0.0.0.0");
        server.getTomcat().getService().addConnector(created);
        if (created.getState() != LifecycleState.STARTED) {
            server.getTomcat().getService().removeConnector(created);
            throw new IllegalStateException("puerto " + lan.getPort() + " ocupado");
        }
        connector = created;
        log.info("Caja del celular abierta en el puerto {}.", lan.getPort());
    }

    /** null en los tests (sin servidor de verdad). */
    private TomcatWebServer tomcat() {
        return context instanceof ServletWebServerApplicationContext web && web.getWebServer() instanceof TomcatWebServer server
                ? server : null;
    }

    public synchronized void close() {
        TomcatWebServer server = tomcat();
        if (connector == null || server == null) {
            connector = null;
            return;
        }
        server.getTomcat().getService().removeConnector(connector);
        try {
            connector.destroy();
        } catch (Exception e) {
            log.debug("Al cerrar el puerto del celular: {}", e.getMessage());
        }
        connector = null;
        log.info("Caja del celular cerrada.");
    }
}
