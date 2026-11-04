package com.tcg.bot.service;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CachingSheetsTransportTest {

    private final AtomicInteger reads = new AtomicInteger();
    private final AtomicInteger writes = new AtomicInteger();

    private final MockHttpTransport google = new MockHttpTransport() {
        @Override
        public LowLevelHttpRequest buildRequest(String method, String url) {
            return new MockLowLevelHttpRequest(url) {
                @Override
                public LowLevelHttpResponse execute() {
                    if ("GET".equals(method)) {
                        reads.incrementAndGet();
                    } else {
                        writes.incrementAndGet();
                    }
                    return new MockLowLevelHttpResponse().setContentType("application/json")
                            .setContent("{\"read\":" + reads.get() + "}");
                }
            };
        }
    };

    private String get(CachingSheetsTransport transport, String url) throws Exception {
        return transport.createRequestFactory().buildGetRequest(new GenericUrl(url)).execute().parseAsString();
    }

    @Test
    void repeatedReadsComeFromMemoryUntilSomethingIsWritten() throws Exception {
        CachingSheetsTransport transport = new CachingSheetsTransport(google);
        Instant start = Instant.parse("2026-11-04T20:00:00Z");
        transport.clock = Clock.fixed(start, ZoneOffset.UTC);

        assertThat(get(transport, "https://sheets.googleapis.com/v4/a")).isEqualTo("{\"read\":1}");
        assertThat(get(transport, "https://sheets.googleapis.com/v4/a")).isEqualTo("{\"read\":1}");
        get(transport, "https://sheets.googleapis.com/v4/b");
        assertThat(reads).hasValue(2);

        transport.createRequestFactory().buildPutRequest(new GenericUrl("https://sheets.googleapis.com/v4/a"),
                ByteArrayContent.fromString("application/json", "{}")).execute();
        assertThat(writes).hasValue(1);

        assertThat(get(transport, "https://sheets.googleapis.com/v4/a")).isEqualTo("{\"read\":3}");
        assertThat(reads).hasValue(3);
    }

    @Test
    void readsExpireAfterAWhileForChangesMadeByHandInTheSheet() throws Exception {
        CachingSheetsTransport transport = new CachingSheetsTransport(google);
        Instant start = Instant.parse("2026-11-04T20:00:00Z");
        transport.clock = Clock.fixed(start, ZoneOffset.UTC);
        get(transport, "https://sheets.googleapis.com/v4/a");

        transport.clock = Clock.fixed(start.plus(CachingSheetsTransport.TTL).plusSeconds(1), ZoneOffset.UTC);
        get(transport, "https://sheets.googleapis.com/v4/a");

        assertThat(reads).hasValue(2);
    }
}
