package com.tcg.bot.service;

import com.tcg.bot.service.BuyService.Line;
import com.tcg.bot.service.BuyService.Payment;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static com.tcg.bot.service.StockOperationsServiceTest.card;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuyServiceTest {

    private StockOperationsServiceTest.FakeStore store;
    private CreditService credit;
    private BuyService service;

    @BeforeEach
    void setUp() {
        store = new StockOperationsServiceTest.FakeStore();
        store.cards.add(card("sol", 2, "Sol Ring", 1, "1500"));
        var stock = new StockOperationsService(store, Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"), ZoneId.of("America/Buenos_Aires")));
        credit = new CreditService(store, stock);
        service = new BuyService(store, stock, credit, new WantedService(store, stock));
    }

    @Test
    void cashPurchaseAddsStockAndTakesMoneyOutOfTheDrawerButNotFromSales() throws Exception {
        var result = service.buy("Ana", "", Payment.CASH, List.of(new Line(store.getInventoryCards().get(0), 2, 700)));

        assertThat(result.formattedTotal()).isEqualTo("1.400");
        assertThat(store.card("sol").getQuantity()).isEqualTo("3");
        assertThat(store.cash).hasSize(1);
        assertThat(store.cash.get(0).isBuy()).isTrue();
        assertThat(store.cash.get(0).affectsCash()).isFalse();
        assertThat(store.cash.get(0).drawerAmount()).isEqualTo(-1400);
        assertThat(store.credits).isEmpty();
    }

    @Test
    void creditPurchaseGivesTheCustomerBalanceAndLeavesTheDrawerAlone() throws Exception {
        var result = service.buy("Ana", "11 5555-1234", Payment.CREDIT, List.of(new Line(store.getInventoryCards().get(0), 1, 900)));

        assertThat(result.newCreditBalance()).isEqualTo(900);
        assertThat(credit.balance(" ana ")).isEqualTo(900);
        assertThat(store.cash.get(0).drawerAmount()).isZero();
        assertThat(store.card("sol").getQuantity()).isEqualTo("2");
    }

    @Test
    void purchasesKeepAnAverageCostPerUnit() throws Exception {
        // La unidad que ya había no tenía costo: se toma lo pagado ahora.
        service.buy("Ana", "", Payment.CASH, List.of(new Line(store.getInventoryCards().get(0), 1, 600)));
        assertThat(store.card("sol").getCost()).isEqualTo("600");

        // 2 unidades a 600 + 2 a 900 = 750 de promedio.
        service.buy("Beto", "", Payment.CREDIT, List.of(new Line(store.getInventoryCards().get(0), 2, 900)));
        assertThat(store.card("sol").getCost()).isEqualTo("750");
        assertThat(store.card("sol").getQuantity()).isEqualTo("4");
    }

    @Test
    void creditNeedsACustomerAndCannotGoNegative() throws Exception {
        assertThatThrownBy(() -> service.buy("", "", Payment.CREDIT, List.of(new Line(store.getInventoryCards().get(0), 1, 900))))
                .isInstanceOf(StockOperationException.class);
        assertThatThrownBy(() -> credit.add("Ana", "", -100, "Ajuste", ""))
                .isInstanceOf(StockOperationException.class);
    }
}
