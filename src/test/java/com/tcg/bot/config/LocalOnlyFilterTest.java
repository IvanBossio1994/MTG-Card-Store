package com.tcg.bot.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LocalOnlyFilterTest {

    private int run(String method, String host, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/ventas");
        request.setServerName(host);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        new LocalOnlyFilter().doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void theAppItselfCanPost() throws Exception {
        assertThat(run("POST", "localhost", "http://localhost:8080")).isEqualTo(200);
        assertThat(run("POST", "127.0.0.1", "http://127.0.0.1:8080")).isEqualTo(200);
        assertThat(run("POST", "localhost", null)).isEqualTo(200);
        assertThat(run("GET", "localhost", null)).isEqualTo(200);
    }

    @Test
    void otherWebsitesCannotPostOrRebind() throws Exception {
        assertThat(run("POST", "localhost", "https://evil.example")).isEqualTo(403);
        assertThat(run("POST", "localhost", "null")).isEqualTo(403);
        assertThat(run("GET", "evil.example", null)).isEqualTo(403);
    }
}
