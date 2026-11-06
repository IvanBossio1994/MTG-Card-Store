package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tcg.bot.service.StockOperationsServiceTest.card;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardDetailsServiceTest {

    private StockOperationsServiceTest.FakeStore store;
    private CardDetailsService service;

    @BeforeEach
    void setUp() {
        store = new StockOperationsServiceTest.FakeStore();
        store.cards.add(card("sol", 2, "Sol Ring", 3, "1500"));
        service = new CardDetailsService(store);
    }

    @Test
    void savesCostAndLocation() throws Exception {
        service.save("sol", 0, "$ 800", "  Carpeta 3 ");

        assertThat(store.card("sol").getCost()).isEqualTo("800");
        assertThat(store.card("sol").getLocation()).isEqualTo("Carpeta 3");
    }

    @Test
    void emptyCostMeansUnknown() throws Exception {
        service.save("sol", 0, "800", "");
        service.save("sol", 0, "", "Caja");

        assertThat(store.card("sol").getCost()).isEmpty();
        assertThat(store.card("sol").costValue()).isNull();
    }

    @Test
    void rejectsNegativeCostAndUnknownCards() {
        assertThatThrownBy(() -> service.save("sol", 0, "-5", "")).isInstanceOf(StockOperationException.class);
        assertThatThrownBy(() -> service.save("otra", 0, "5", "")).isInstanceOf(StockOperationException.class);
    }

    @Test
    void averageCostWeighsWhatWasThereAndWhatCameIn() {
        assertThat(CardDetailsService.averageCost(0, null, 2, 500)).isEqualTo(500);
        assertThat(CardDetailsService.averageCost(3, null, 1, 500)).isEqualTo(500);
        assertThat(CardDetailsService.averageCost(1, 1000.0, 3, 600)).isEqualTo(700);
    }

    @Test
    void salesCarryTheCostAndTheDayShowsTheProfit() throws Exception {
        service.save("sol", 0, "1000", "");
        var stock = TestStores.stock(store);
        var sales = new SalesService(store, stock, new CreditService(store, stock));
        sales.sell(List.of(new ReservationService.Item("sol", 2)));

        CashRegisterEntry sale = store.cash.get(0);
        assertThat(sale.getUnitCost()).isEqualTo("1000");
        assertThat(sale.profit()).isEqualTo(1000);

        var close = new CashCloseService(store, stock);
        var profit = close.day(close.today()).profit();
        assertThat(profit.known()).isTrue();
        assertThat(profit.value()).isEqualTo(1000);
        assertThat(profit.withoutCost()).isZero();
    }

    @Test
    void salesWithoutCostAreCountedApart() throws Exception {
        var stock = TestStores.stock(store);
        new SalesService(store, stock, new CreditService(store, stock)).sell(List.of(new ReservationService.Item("sol", 1)));

        var close = new CashCloseService(store, stock);
        var profit = close.day(close.today()).profit();
        assertThat(profit.known()).isFalse();
        assertThat(profit.withoutCost()).isEqualTo(1);
    }
}
