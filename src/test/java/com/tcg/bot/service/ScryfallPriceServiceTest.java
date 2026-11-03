package com.tcg.bot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ScryfallPriceServiceTest {

    private static final String RESPONSE = """
            {"object":"list","not_found":[],"data":[
              {"set":"cmm","collector_number":"410","prices":{"usd":"2.10","usd_foil":"5.00","eur":"1.80","eur_foil":null}},
              {"set":"lea","collector_number":"54","prices":{"usd":null,"usd_foil":null,"eur":"9000.00","eur_foil":null}}
            ]}
            """;

    @TempDir
    Path dir;

    @Test
    void parsesPricesAndMatchesCardKingdomNumbers() {
        List<String> bodies = new ArrayList<>();
        var service = new ScryfallPriceService(body -> {
            bodies.add(body);
            return RESPONSE;
        }, Clock.systemUTC(), 0);

        var prices = service.prices(List.of(new ScryfallPriceService.CardRef("CMM", "0410")));

        assertThat(bodies.get(0)).contains("\"set\":\"cmm\"").contains("\"collector_number\":\"410\"");
        var sol = prices.get(ScryfallPriceService.key("CMM", "0410"));
        assertThat(sol.tcgplayer(false)).isEqualTo(2.10);
        assertThat(sol.tcgplayer(true)).isEqualTo(5.00);
        assertThat(sol.cardmarket(true)).isEqualTo(1.80);
    }

    @Test
    void batchesOf75AndCaches() {
        List<String> bodies = new ArrayList<>();
        var service = new ScryfallPriceService(body -> {
            bodies.add(body);
            return "{\"data\":[]}";
        }, Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC), 0);
        var cards = IntStream.range(1, 161)
                .mapToObj(i -> new ScryfallPriceService.CardRef("cmm", String.valueOf(i)))
                .toList();

        service.prices(cards);
        service.prices(cards);

        assertThat(bodies).hasSize(3);
    }

    @Test
    void failureFallsBackToNothing() {
        var service = new ScryfallPriceService(body -> {
            throw new java.io.IOException("sin internet");
        }, Clock.systemUTC(), 0);

        assertThat(service.prices(List.of(new ScryfallPriceService.CardRef("cmm", "410")))).isEmpty();
    }

    @Test
    void combinesSourcesIntoLocalPrice() throws Exception {
        var settings = new PricingSettingsService(1000, 100, dir.toString());
        settings.update(1000, 100, 0, 0);
        settings.updateSources(true, true, 1100, PricingSettingsService.CombineMode.AVERAGE);
        var comparison = new PriceComparisonService(settings);
        var scryfall = new ScryfallPriceService.Prices(2.0, null, 3.0, null);

        // CK 1 USD = 1000, TCG 2 USD = 2000, CM 3 EUR = 3300 -> promedio 2100
        var quote = comparison.quote(1.0, scryfall, false);
        assertThat(quote.sources()).hasSize(3);
        assertThat(quote.localPrice()).isEqualTo(2100);

        settings.updateSources(true, true, 1100, PricingSettingsService.CombineMode.MAX);
        assertThat(comparison.quote(1.0, scryfall, false).localPrice()).isEqualTo(3300);

        settings.updateSources(true, true, 1100, PricingSettingsService.CombineMode.MIN);
        assertThat(comparison.quote(1.0, scryfall, false).localPrice()).isEqualTo(1000);

        settings.updateSources(false, false, 0, PricingSettingsService.CombineMode.CARD_KINGDOM);
        assertThat(comparison.quote(1.0, scryfall, false).sources()).hasSize(1);
        assertThat(comparison.quote(1.0, scryfall, false).localPrice()).isEqualTo(1000);
    }
}
