package com.tcg.bot.service;

import com.tcg.bot.model.WantedCard;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.tcg.bot.service.StockOperationsServiceTest.card;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WantedServiceTest {

    private StockOperationsServiceTest.FakeStore store;
    private StockOperationsService stock;
    private WantedService service;

    @BeforeEach
    void setUp() {
        store = new StockOperationsServiceTest.FakeStore();
        store.cards.add(card("sol", 2, "Sol Ring", 0, "1500"));
        stock = TestStores.stock(store);
        service = new WantedService(store, stock);
    }

    @Test
    void addsAListForACustomer() throws Exception {
        var added = service.add(" Juan ", "11 5555-1234", "Sideboard\n1 Sol Ring\n2 Fatal Push (2XM)");

        assertThat(added).hasSize(2);
        assertThat(store.wanted).extracting(WantedCard::getName).containsExactly("Sol Ring", "Fatal Push");
        assertThat(store.wanted.get(1).getSetCode()).isEqualTo("2XM");
        assertThat(store.wanted.get(1).getQuantity()).isEqualTo(2);
        assertThat(store.wanted.get(0).getCustomer()).isEqualTo("Juan");
        assertThat(store.wanted.get(0).getStatus()).isEqualTo(WantedCard.STATUS_WANTED);
    }

    @Test
    void needsACustomerAndAtLeastOneCard() {
        assertThatThrownBy(() -> service.add("", "", "1 Sol Ring")).isInstanceOf(StockOperationException.class);
        assertThatThrownBy(() -> service.add("Juan", "", "Sideboard")).isInstanceOf(StockOperationException.class);
    }

    @Test
    void showsArrivedFirstWhenTheCardEntersStock() throws Exception {
        service.add("Juan", "", "1 Sol Ring");
        service.add("Ana", "", "1 Fatal Push");
        assertThat(service.open()).noneMatch(WantedService.View::arrived);
        assertThat(WantedService.arrivedCount(store.getWantedCards(), store.getAllStockCards())).isZero();

        // Le compran un Sol Ring a otro cliente: entra al stock.
        var buy = new BuyService(store, stock, new CreditService(store, stock), service);
        var result = buy.buy("Beto", "", BuyService.Payment.CASH, List.of(new BuyService.Line(store.getInventoryCards().get(0), 1, 500)));

        assertThat(result.waiting()).extracting(WantedCard::getCustomer).containsExactly("Juan");
        var open = service.open();
        assertThat(open.get(0).arrived()).isTrue();
        assertThat(open.get(0).wanted().getCustomer()).isEqualTo("Juan");
        assertThat(WantedService.arrivedCount(store.getWantedCards(), store.getAllStockCards())).isEqualTo(1);
    }

    @Test
    void notifiedAndClosedCardsStopCounting() throws Exception {
        store.card("sol").setQuantity("1");
        String id = service.add("Juan", "", "1 Sol Ring").get(0).getId();

        service.markNotified(id);
        assertThat(WantedService.arrivedCount(store.getWantedCards(), store.getAllStockCards())).isZero();
        assertThat(service.open()).hasSize(1);

        service.close(id);
        assertThat(service.open()).isEmpty();
    }

    @Test
    void anEditionOnlyMatchesThatEdition() throws Exception {
        store.card("sol").setQuantity("1");
        service.add("Juan", "", "1 Sol Ring (C21)");

        assertThat(service.open().get(0).arrived()).isFalse();
    }
}
