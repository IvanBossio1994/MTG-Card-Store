package com.tcg.bot.service;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.TransportBridge;
import com.google.api.client.http.LowLevelHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Conexión con Google Sheets que recuerda las lecturas un rato.
 * <p>
 * Cada pantalla lee varias pestañas del Sheet y cada lectura tarda lo que tarda Google en responder.
 * Una lectura (GET) igual a otra reciente se responde desde memoria. Cualquier escritura (POST, PUT…)
 * borra todo lo recordado, así lo que la app cambia se ve enseguida. Lo que se edita a mano en el Sheet
 * aparece a más tardar en {@link #TTL} (o al tocar Sincronizar).
 */
public class CachingSheetsTransport extends HttpTransport {

    static final Duration TTL = Duration.ofMinutes(5);

    private record Stored(Instant at, int status, String reason, String contentType, String contentEncoding,
                          List<String[]> headers, byte[] body) {
    }

    private final Map<String, Stored> cache = new ConcurrentHashMap<>();
    /** Sube con cada escritura: una lectura que empezó antes no se guarda. */
    private final AtomicLong generation = new AtomicLong();
    Clock clock = Clock.systemUTC();
    private final HttpTransport real;

    public CachingSheetsTransport(HttpTransport real) {
        this.real = real;
    }

    @Override
    public boolean supportsMethod(String method) throws IOException {
        return real.supportsMethod(method);
    }

    @Override
    public void shutdown() throws IOException {
        real.shutdown();
    }

    public void clear() {
        generation.incrementAndGet();
        cache.clear();
    }

    @Override
    protected LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        LowLevelHttpRequest real = TransportBridge.buildRequest(this.real, method, url);
        if (!"GET".equalsIgnoreCase(method)) {
            clear();
            return new Writing(real);
        }
        return new Reading(url, real);
    }

    /** Escritura: además de borrar antes de mandar, borra al terminar (por si una lectura se coló en el medio). */
    private final class Writing extends LowLevelHttpRequest {
        private final LowLevelHttpRequest real;

        Writing(LowLevelHttpRequest real) {
            this.real = real;
        }

        @Override
        public void addHeader(String name, String value) throws IOException {
            real.addHeader(name, value);
        }

        @Override
        public void setTimeout(int connectTimeout, int readTimeout) throws IOException {
            real.setTimeout(connectTimeout, readTimeout);
        }

        @Override
        public void setWriteTimeout(int writeTimeout) throws IOException {
            real.setWriteTimeout(writeTimeout);
        }

        @Override
        public LowLevelHttpResponse execute() throws IOException {
            real.setContentType(getContentType());
            real.setContentEncoding(getContentEncoding());
            real.setContentLength(getContentLength());
            real.setStreamingContent(getStreamingContent());
            try {
                return real.execute();
            } finally {
                clear();
            }
        }
    }

    private final class Reading extends LowLevelHttpRequest {
        private final String url;
        private final LowLevelHttpRequest real;

        Reading(String url, LowLevelHttpRequest real) {
            this.url = url;
            this.real = real;
        }

        @Override
        public void addHeader(String name, String value) throws IOException {
            real.addHeader(name, value);
        }

        @Override
        public void setTimeout(int connectTimeout, int readTimeout) throws IOException {
            real.setTimeout(connectTimeout, readTimeout);
        }

        @Override
        public void setWriteTimeout(int writeTimeout) throws IOException {
            real.setWriteTimeout(writeTimeout);
        }

        @Override
        public LowLevelHttpResponse execute() throws IOException {
            Instant now = clock.instant();
            Stored hit = cache.get(url);
            if (hit != null && hit.at().plus(TTL).isAfter(now)) {
                return new Replay(hit);
            }
            long started = generation.get();
            LowLevelHttpResponse response = real.execute();
            byte[] body;
            try (InputStream content = response.getContent()) {
                body = content == null ? new byte[0] : content.readAllBytes();
            } finally {
                response.disconnect();
            }
            List<String[]> headers = new ArrayList<>();
            for (int index = 0; index < response.getHeaderCount(); index++) {
                headers.add(new String[]{response.getHeaderName(index), response.getHeaderValue(index)});
            }
            Stored stored = new Stored(now, response.getStatusCode(), response.getReasonPhrase(),
                    response.getContentType(), response.getContentEncoding(), headers, body);
            if (stored.status() == 200 && generation.get() == started) {
                cache.put(url, stored);
            }
            return new Replay(stored);
        }
    }

    private static final class Replay extends LowLevelHttpResponse {
        private final Stored stored;

        Replay(Stored stored) {
            this.stored = stored;
        }

        @Override
        public InputStream getContent() {
            return new ByteArrayInputStream(stored.body());
        }

        @Override
        public String getContentEncoding() {
            return stored.contentEncoding();
        }

        @Override
        public long getContentLength() {
            return stored.body().length;
        }

        @Override
        public String getContentType() {
            return stored.contentType();
        }

        @Override
        public String getStatusLine() {
            return "HTTP/1.1 " + stored.status() + " " + (stored.reason() == null ? "" : stored.reason());
        }

        @Override
        public int getStatusCode() {
            return stored.status();
        }

        @Override
        public String getReasonPhrase() {
            return stored.reason();
        }

        @Override
        public int getHeaderCount() {
            return stored.headers().size();
        }

        @Override
        public String getHeaderName(int index) {
            return stored.headers().get(index)[0];
        }

        @Override
        public String getHeaderValue(int index) {
            return stored.headers().get(index)[1];
        }
    }
}
