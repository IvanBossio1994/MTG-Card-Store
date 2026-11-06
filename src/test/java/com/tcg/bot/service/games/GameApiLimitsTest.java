package com.tcg.bot.service.games;

import com.tcg.bot.model.Game;
import com.tcg.bot.model.GameCard;
import com.tcg.bot.model.GamePrint;
import com.tcg.bot.service.CreditService;
import com.tcg.bot.service.PriceComparisonService;
import com.tcg.bot.service.ReservationService;
import com.tcg.bot.service.SalesService;
import com.tcg.bot.service.StockOperationsBridge;
import com.tcg.bot.service.StockOperationsService;
import com.tcg.bot.service.TestStores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Las consultas a los catálogos se guardan 1 h 5 min y las cartas de otros juegos se operan como las de Magic. */
class GameApiLimitsTest {

    private static final GamePrint PIKACHU = new GamePrint("sv03-63:normal", "Pikachu", "Obsidian Flames", "SV03", "63",
            "Common", "Normal", "", 1.0, "");

    private TestStores.Fake store;
    private StockOperationsService stock;
    private GameInventoryService service;
    private final AtomicInteger calls = new AtomicInteger();
    private boolean catalogDown;

    @BeforeEach
    void setUp() {
        store = TestStores.fake();
        PriceComparisonService prices = mock(PriceComparisonService.class);
        when(prices.calculateLocalPrice(anyDouble())).thenAnswer(call -> (double) call.getArgument(0) * 1000);
        stock = TestStores.stock(store);
        GameCatalog catalog = new GameCatalog() {
            public Game game() { return Game.POKEMON; }
            public List<GamePrint> search(String name) {
                calls.incrementAndGet();
                if (catalogDown) {
                    throw new IllegalStateException("El catálogo respondió 429");
                }
                return List.of(PIKACHU);
            }
        };
        service = new GameInventoryService(store, new StockOperationsBridge(store, stock), prices, List.of(catalog));
    }

    @Test
    void theSameSearchIsAskedOnlyOncePerHourAndFiveMinutes() throws Exception {
        Instant start = Instant.parse("2026-11-03T15:00:00Z");
        service.clock = Clock.fixed(start, ZoneOffset.UTC);
        service.search(Game.POKEMON, "Pikachu");
        service.search(Game.POKEMON, " pikachu ");
        assertThat(calls).hasValue(1);

        service.clock = Clock.fixed(start.plus(Duration.ofMinutes(64)), ZoneOffset.UTC);
        service.search(Game.POKEMON, "Pikachu");
        assertThat(calls).hasValue(1);

        service.clock = Clock.fixed(start.plus(Duration.ofMinutes(66)), ZoneOffset.UTC);
        service.search(Game.POKEMON, "Pikachu");
        assertThat(calls).hasValue(2);
    }

    @Test
    void whenTheCatalogFailsAnOldAnswerIsBetterThanAnError() throws Exception {
        Instant start = Instant.parse("2026-11-03T15:00:00Z");
        service.clock = Clock.fixed(start, ZoneOffset.UTC);
        service.search(Game.POKEMON, "Pikachu");

        catalogDown = true;
        service.clock = Clock.fixed(start.plus(Duration.ofHours(3)), ZoneOffset.UTC);
        assertThat(service.search(Game.POKEMON, "Pikachu")).hasSize(1);
        assertThatThrownBy(() -> service.search(Game.POKEMON, "Charizard")).hasMessageContaining("429");
    }

    @Test
    void cardsLoadedFromAnotherSourceStillMatchByNameSetNumberAndFinish() throws Exception {
        GameCard old = new GameCard();
        old.setQuantity(2);
        old.setName("Pikachu");
        old.setSetName("Obsidian Flames");
        old.setSetCode("OBF");
        old.setNumber("63");
        old.setFinish("Normal");
        old.setId("Gold");
        old.setCatalogId("sv3-63:normal");
        store.appendGameCard(Game.POKEMON, old);

        var results = service.search(Game.POKEMON, "Pikachu");

        assertThat(results.get(0).stock()).isEqualTo(2);
        assertThat(service.ensureRow(Game.POKEMON, PIKACHU).getId()).isEqualTo("Gold");
    }

    @Test
    void buyingCreatesAnEmptyRowAndTheCartSellsOtherGamesLikeMagic() throws Exception {
        GameCard row = service.ensureRow(Game.POKEMON, PIKACHU);
        assertThat(row.getQuantity()).isZero();
        assertThat(TestStores.cash(store)).isEmpty();

        service.change(Game.POKEMON, row.getId(), 1, false);
        service.change(Game.POKEMON, row.getId(), 1, false);

        SalesService sales = new SalesService(store, stock, new CreditService(store, stock));
        var ticket = sales.sell(List.of(new ReservationService.Item(row.getId(), 2)));

        assertThat(ticket.total()).isEqualTo(2000);
        assertThat(store.getGameCards(Game.POKEMON).get(0).getQuantity()).isZero();
        assertThat(TestStores.cash(store)).hasSize(1);
        assertThat(TestStores.cash(store).get(0).getSetName()).isEqualTo("Pokémon · Obsidian Flames");

        // Anular la venta devuelve el stock a la pestaña de Pokémon.
        stock.voidSale(TestStores.cash(store).get(0).getSaleId());
        assertThat(store.getGameCards(Game.POKEMON).get(0).getQuantity()).isEqualTo(2);
    }
}
