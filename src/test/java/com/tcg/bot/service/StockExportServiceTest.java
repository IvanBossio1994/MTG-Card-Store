package com.tcg.bot.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import static com.tcg.bot.service.StockOperationsServiceTest.card;
import static org.assertj.core.api.Assertions.assertThat;

class StockExportServiceTest {

    @Test
    void exportsOnlyCardsInStockWithTheMarketplaceIds() throws Exception {
        var store = new StockOperationsServiceTest.FakeStore();
        var sol = card("sol", 2, "Sol Ring", 3, "1500");
        sol.setCollectorNumber("0410");
        sol.setCondition("LP");
        sol.setLanguage("Español");
        sol.setLocation("Carpeta 1, pág. 2");
        store.cards.add(sol);
        var gone = card("gone", 3, "Counterspell", 0, "900");
        store.cards.add(gone);

        String response = """
                {"data":[{"id":"abc-123","set":"cmm","collector_number":"410","tcgplayer_id":501234,"cardmarket_id":712345,
                "prices":{"usd":"1.10"}}]}""";
        var scryfall = new ScryfallPriceService(body -> response, Clock.systemUTC(), 0);
        String csv = new StockExportService(store, scryfall).magicCsv();

        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).startsWith("Count,Name,Edition,Set Name,Collector Number,Foil,Condition,Language,Price");
        assertThat(lines[1]).isEqualTo("3,Sol Ring,cmm,Commander Masters,410,,Lightly Played,Spanish,1500,abc-123,501234,712345,\"Carpeta 1, pág. 2\"");
    }

    @Test
    void cellsAreQuotedAndNeverFormulas() {
        assertThat(StockExportService.cell("Fire // Ice")).isEqualTo("Fire // Ice");
        assertThat(StockExportService.cell("Borrowed Time, \"BT\"")).isEqualTo("\"Borrowed Time, \"\"BT\"\"\"");
        assertThat(StockExportService.cell("=HYPERLINK(1)")).isEqualTo("'=HYPERLINK(1)");
        assertThat(StockExportService.cell("-5")).isEqualTo("-5");
    }

    @Test
    void conditionsAndLanguagesUseTheLongNames() {
        assertThat(StockExportService.condition("NM")).isEqualTo("Near Mint");
        assertThat(StockExportService.condition("")).isEqualTo("Near Mint");
        assertThat(StockExportService.condition("HP")).isEqualTo("Heavily Played");
        assertThat(StockExportService.language("JP")).isEqualTo("Japanese");
        assertThat(StockExportService.language("Inglés")).isEqualTo("English");
    }
}
