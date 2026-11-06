package com.tcg.bot.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.tcg.bot.service.StockOperationsServiceTest.card;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicCatalogServiceTest {

    @TempDir
    Path dir;

    private StockOperationsServiceTest.FakeStore store;
    private GoogleSheetsService sheets;
    private PublicCatalogService service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("es"));
        store = new StockOperationsServiceTest.FakeStore();
        store.cards.add(card("sol", 2, "Sol Ring", 2, "1500"));
        store.cards.add(card("bolt", 3, "Lightning Bolt", 5, "2000"));
        store.cards.add(card("none", 4, "Counterspell", 0, "5000"));
        store.cards.add(card("free", 5, "Island", 3, ""));
        sheets = mock(GoogleSheetsService.class);
        StoreSettingsService settings = mock(StoreSettingsService.class);
        when(settings.getStoreName()).thenReturn("La Cueva");
        service = new PublicCatalogService(store, sheets, settings, TestStores.stock(store), dir.toString());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void catalogOnlyHasCardsWithStockAndPriceAndNoPrivateData() {
        var rows = PublicCatalogService.catalogRows(store.cards);

        assertThat(rows.get(0)).containsExactly("Código", "Carta", "Edición", "Número", "Foil", "Estado", "Idioma",
                "Precio", "Disponibles");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(1)).startsWith("bolt", "Lightning Bolt", "Commander Masters", "410");
        assertThat(rows.get(1)).endsWith("$ 2.000", 5);
        assertThat(rows.get(2).get(1)).isEqualTo("Sol Ring");
    }

    @Test
    void buylistHasPricedCardsBelowTargetWithWhatWePay() {
        var settings = new PublicCatalogService.Settings("", 50, 60, 4, 1000, "");
        var rows = PublicCatalogService.buylistRows(store.cards, settings);

        // Bolt ya tiene 5 (más que 4) e Island no tiene precio: quedan Counterspell (0 de 4) y Sol Ring (2 de 4).
        assertThat(rows).hasSize(3);
        assertThat(rows.get(1)).containsExactly("Counterspell", "Commander Masters", "410", "", "$ 2.500", "$ 3.000", 4);
        assertThat(rows.get(2)).containsExactly("Sol Ring", "Commander Masters", "410", "", "$ 750", "$ 900", 2);
    }

    @Test
    void buylistAddsUpTheSamePrintAcrossRowsAndRespectsMinimumPrice() {
        var second = card("sol2", 6, "Sol Ring", 2, "1400");
        second.setCondition("LP");
        var cards = List.of(store.cards.get(0), second, store.cards.get(2));

        var rows = PublicCatalogService.buylistRows(cards, new PublicCatalogService.Settings("", 50, 60, 4, 2000, ""));

        // Sol Ring suma 4 copias: no entra. Counterspell sí (precio 5000 ≥ 2000).
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).get(0)).isEqualTo("Counterspell");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishWritesBothTabsAndRemembersTheSheet() throws Exception {
        when(sheets.usesGoogleAccount()).thenReturn(true);
        when(sheets.writePublicSpreadsheet(eq(""), anyString(), any())).thenReturn("PUB123");

        var result = service.publish();

        ArgumentCaptor<Map<String, List<List<Object>>>> tabs = ArgumentCaptor.forClass(Map.class);
        verify(sheets).writePublicSpreadsheet(eq(""), eq("La Cueva – Catálogo y buylist"), tabs.capture());
        assertThat(tabs.getValue().keySet()).containsExactly("Catálogo", "Buylist");
        assertThat(result.catalogCards()).isEqualTo(2);
        assertThat(result.link()).contains("PUB123");
        assertThat(service.settings().lastPublished()).isNotBlank();

        // La segunda vez reusa el mismo Sheet, también después de reiniciar la app.
        var reloaded = new PublicCatalogService(store, sheets, mock(StoreSettingsService.class), TestStores.stock(store), dir.toString());
        assertThat(reloaded.settings().spreadsheetId()).isEqualTo("PUB123");
    }

    @Test
    void publishNeedsAGoogleAccount() {
        when(sheets.usesGoogleAccount()).thenReturn(false);
        assertThatThrownBy(service::publish).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void settingsAreKeptWithinLimits() throws Exception {
        service.saveSettings(150, -5, 0, -10);
        var settings = service.settings();
        assertThat(settings.cashPercent()).isEqualTo(100);
        assertThat(settings.creditPercent()).isZero();
        assertThat(settings.target()).isEqualTo(1);
        assertThat(settings.minPrice()).isZero();
    }
}
