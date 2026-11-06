package com.tcg.bot.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LanAccessTest {

    @TempDir
    Path dir;

    @Test
    void pinGivesASessionAndChangingItLogsEveryoneOut() throws Exception {
        LanAccess lan = new LanAccess(dir.toString());
        assertThat(lan.getPin()).matches("\\d{6}");

        String session = lan.login(lan.getPin());
        assertThat(lan.isValidSession(session)).isTrue();
        assertThat(lan.login("000000".equals(lan.getPin()) ? "111111" : "000000")).isNull();

        lan.changePin();
        assertThat(lan.isValidSession(session)).isFalse();
        // El PIN queda guardado para la próxima vez que se abra la app.
        assertThat(new LanAccess(dir.toString()).getPin()).isEqualTo(lan.getPin());
    }

    @Test
    void tooManyWrongPinsLockForAMinute() {
        LanAccess lan = new LanAccess(dir.toString());
        Instant start = Instant.parse("2026-11-06T10:00:00Z");
        lan.clock = Clock.fixed(start, ZoneOffset.UTC);
        String wrong = "000000".equals(lan.getPin()) ? "111111" : "000000";
        for (int i = 0; i < LanAccess.MAX_FAILURES; i++) {
            lan.login(wrong);
        }
        assertThat(lan.isLocked()).isTrue();
        assertThat(lan.login(lan.getPin())).isNull();

        lan.clock = Clock.fixed(start.plus(Duration.ofSeconds(LanAccess.LOCK_SECONDS + 1)), ZoneOffset.UTC);
        assertThat(lan.login(lan.getPin())).isNotNull();
    }

    @Test
    void onlyPrivateAddresses() {
        assertThat(LanAccess.isPrivate("192.168.0.10")).isTrue();
        assertThat(LanAccess.isPrivate("10.1.2.3")).isTrue();
        assertThat(LanAccess.isPrivate("172.20.0.5")).isTrue();
        assertThat(LanAccess.isPrivate("172.32.0.5")).isFalse();
        assertThat(LanAccess.isPrivate("8.8.8.8")).isFalse();
        assertThat(LanAccess.isPrivate("evil.example")).isFalse();
        assertThat(LanAccess.isPrivate("192.168.0.300")).isFalse();
    }

    private int phone(LanAccess lan, String method, String path, String host, String origin, String session) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServerName(host);
        request.setServerPort(lan.getPort());
        request.setLocalPort(lan.getPort());
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        if (session != null) {
            request.setCookies(new Cookie(LanAccess.COOKIE, session));
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new LocalOnlyFilter(lan).doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void phoneNeedsTheSwitchOnAPrivateAddressAndThePin() throws Exception {
        LanAccess lan = new LanAccess(dir.toString());
        String ip = "192.168.0.10";

        assertThat(phone(lan, "GET", "/", ip, null, null)).isEqualTo(403);
        lan.setEnabled(true);
        // Sin PIN: a la pantalla del PIN.
        assertThat(phone(lan, "GET", "/", ip, null, null)).isEqualTo(302);
        assertThat(phone(lan, "GET", "/celular", ip, null, null)).isEqualTo(200);
        assertThat(phone(lan, "POST", "/ventas", ip, null, null)).isEqualTo(401);

        String session = lan.login(lan.getPin());
        assertThat(phone(lan, "GET", "/", ip, null, session)).isEqualTo(200);
        assertThat(phone(lan, "POST", "/ventas", ip, "http://192.168.0.10:" + lan.getPort(), session)).isEqualTo(200);
        assertThat(phone(lan, "POST", "/reservas/R1/retirar", ip, null, session)).isEqualTo(200);
        // Lo de administración no se puede desde el celular.
        assertThat(phone(lan, "GET", "/configuracion", ip, null, session)).isEqualTo(403);
        assertThat(phone(lan, "POST", "/cuenta/google/desconectar", ip, null, session)).isEqualTo(403);
        assertThat(phone(lan, "GET", "/movimientos", ip, null, session)).isEqualTo(403);
        assertThat(phone(lan, "POST", "/segunda-caja", ip, null, session)).isEqualTo(403);
        // Otra web o un dominio que apunte a la PC, no.
        assertThat(phone(lan, "POST", "/ventas", ip, "https://evil.example", session)).isEqualTo(403);
        assertThat(phone(lan, "GET", "/", "evil.example", null, session)).isEqualTo(403);

        lan.setEnabled(false);
        assertThat(phone(lan, "GET", "/", ip, null, session)).isEqualTo(403);
    }
}
