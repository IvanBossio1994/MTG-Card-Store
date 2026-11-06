package com.tcg.bot.service.games;

import com.tcg.bot.model.Game;
import com.tcg.bot.model.GamePrint;
import com.tcg.bot.service.PriceComparisonService;
import com.tcg.bot.service.StockOperationsBridge;
import com.tcg.bot.service.StockOperationsService;
import com.tcg.bot.service.TestStores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameInventoryServiceTest {

    private static final GamePrint CHARIZARD = new GamePrint("base1-4:holofoil", "Charizard", "Base", "BS", "4",
            "Rare Holo", "Holo", "", 10.0, "");

    private TestStores.Fake store;
    private GameInventoryService service;
    private double usdSeen;

    @BeforeEach
    void setUp() {
        store = TestStores.fake();
        PriceComparisonService prices = mock(PriceComparisonService.class);
        when(prices.calculateLocalPrice(anyDouble())).thenAnswer(call -> (double) call.getArgument(0) * 1000);
        StockOperationsService stock = TestStores.stock(store);
        GameCatalog catalog = new GameCatalog() {
            public Game game() { return Game.POKEMON; }
            public List<GamePrint> search(String name) {
                return List.of(new GamePrint(CHARIZARD.catalogId(), "Charizard", "Base", "BS", "4", "Rare Holo", "Holo", "", usdSeen, ""));
            }
        };
        service = new GameInventoryService(store, new StockOperationsBridge(store, stock), prices, List.of(catalog));
    }

    @Test
    void addingCreatesTheRowOnceAndSellingGoesToTheSharedCash() throws Exception {
        service.add(Game.POKEMON, CHARIZARD);
        var card = service.add(Game.POKEMON, CHARIZARD);

        assertThat(store.getGameCards(Game.POKEMON)).hasSize(1);
        assertThat(card.getQuantity()).isEqualTo(2);
        assertThat(card.getLocalPrice()).isEqualTo(10000);

        service.change(Game.POKEMON, card.getId(), -1, true);

        assertThat(store.getGameCards(Game.POKEMON).get(0).getQuantity()).isEqualTo(1);
        assertThat(TestStores.cash(store)).hasSize(1);
        assertThat(TestStores.cash(store).get(0).getSetName()).isEqualTo("Pokémon · Base");
        assertThat(TestStores.cash(store).get(0).signedTotal()).isEqualTo(10000);
    }

    @Test
    void searchShowsWhatIsInStockAndRefreshUpdatesPrices() throws Exception {
        usdSeen = 10.0;
        service.add(Game.POKEMON, CHARIZARD);

        var results = service.search(Game.POKEMON, "char");
        assertThat(results.get(0).stock()).isEqualTo(1);

        usdSeen = 12.5;
        assertThat(service.refreshPrices(Game.POKEMON)).isEqualTo(1);
        assertThat(store.getGameCards(Game.POKEMON).get(0).getLocalPrice()).isEqualTo(12500);
    }

}
