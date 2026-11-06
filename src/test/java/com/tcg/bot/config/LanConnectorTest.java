package com.tcg.bot.config;

import com.tcg.bot.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Con servidor de verdad: el puerto del celular se abre y se cierra sin reiniciar la app. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LanConnectorTest {

    static Path dir;
    static int lanPort;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) throws IOException {
        dir = Files.createTempDirectory("tcg-lan");
        try (ServerSocket socket = new ServerSocket(0)) {
            lanPort = socket.getLocalPort();
        }
        Files.writeString(dir.resolve("lan.properties"), "enabled=false\nport=" + lanPort + "\npin=123456\n");
        registry.add("app.storage-dir", () -> dir.toString());
    }

    @MockitoBean
    InventoryService inventoryService;

    @Autowired
    LanConnector connector;

    @Autowired
    LanAccess lan;

    private static String get(String host, String path, String cookie) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", lanPort)) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + path + " HTTP/1.1\r\nHost: " + host + ":" + lanPort + "\r\n"
                    + (cookie == null ? "" : "Cookie: " + cookie + "\r\n")
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
            InputStream in = socket.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void opensAndClosesThePhonePort() throws Exception {
        assertThatThrownBy(() -> get("192.168.1.5", "/celular", null)).isInstanceOf(ConnectException.class);

        lan.setEnabled(true);
        connector.open();
        assertThat(connector.isOpen()).isTrue();

        assertThat(get("192.168.1.5", "/celular", null)).startsWith("HTTP/1.1 200").contains("name=\"pin\"");
        assertThat(get("192.168.1.5", "/", null)).startsWith("HTTP/1.1 302");
        assertThat(get("example.com", "/celular", null)).startsWith("HTTP/1.1 403");
        String session = lan.login("123456");
        assertThat(get("192.168.1.5", "/configuracion", LanAccess.COOKIE + "=" + session)).startsWith("HTTP/1.1 403");
        // Con PIN entra a buscar y vender, con el menú corto (sin caja ni configuración).
        String home = get("192.168.1.5", "/", LanAccess.COOKIE + "=" + session);
        assertThat(home).startsWith("HTTP/1.1 200").contains("href=\"/reservas\"").doesNotContain("href=\"/movimientos\"").doesNotContain("href=\"/segunda-caja\"");

        connector.close();
        lan.setEnabled(false);
        assertThatThrownBy(() -> get("192.168.1.5", "/celular", null)).isInstanceOf(ConnectException.class);
    }
}
