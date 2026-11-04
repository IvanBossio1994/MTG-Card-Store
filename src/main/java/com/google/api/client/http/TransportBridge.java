package com.google.api.client.http;

import java.io.IOException;

/**
 * Puente mínimo para envolver un transporte de google-http-client: {@code buildRequest} es protegido,
 * así que solo se puede llamar desde este paquete. Lo usa {@code com.tcg.bot.service.CachingSheetsTransport}.
 */
public final class TransportBridge {

    private TransportBridge() {
    }

    public static LowLevelHttpRequest buildRequest(HttpTransport transport, String method, String url) throws IOException {
        return transport.buildRequest(method, url);
    }
}
