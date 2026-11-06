package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.Consignment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;

import static com.tcg.bot.service.StockOperationsServiceTest.card;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsignmentServiceTest {

    private StockOperationsServiceTest.FakeStore store;
    private StockOperationsService stock;
    private CreditService credit;
    private ConsignmentService service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("es"));
        store = new StockOperationsServiceTest.FakeStore();
        store.cards.add(card("sheol", 2, "Sheoldred", 1, "10000"));
        stock = TestStores.stock(store);
        credit = new CreditService(store, stock);
        service = new ConsignmentService(store, stock, credit);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private Consignment receiveTwo() throws Exception {
        return service.receive("Juan", "11 5555-1234", 70,
                List.of(new ConsignmentService.Line(store.card("sheol"), 2))).get(0);
    }

    @Test
    void receivingAddsASeparateRowWithTheOwnersShareAsCost() throws Exception {
        Consignment item = receiveTwo();

        assertThat(store.cards).hasSize(2);
        var copy = store.card(item.getCardId());
        assertThat(copy.getId()).isNotEqualTo("sheol");
        assertThat(copy.getQuantity()).isEqualTo("2");
        assertThat(copy.getLocalPrice()).isEqualTo("10000");
        assertThat(copy.getCost()).isEqualTo("7000");
        assertThat(copy.getLocation()).isEqualTo("Consignación: Juan");
        // La fila de la tienda no se toca.
        assertThat(store.card("sheol").getQuantity()).isEqualTo("1");
        assertThat(store.consignments).hasSize(1);
        assertThat(store.movements).hasSize(1);
    }

    @Test
    void owedComesFromSalesOfThatRowMinusVoids() throws Exception {
        Consignment item = receiveTwo();
        var copy = store.card(item.getCardId());

        var sale = stock.changeStock(copy.getId(), copy.getRowIndex(), -2, StockOperationsService.Reason.SALE);
        stock.voidSale(sale.saleId());
        stock.changeStock(copy.getId(), copy.getRowIndex(), -1, StockOperationsService.Reason.SALE);
        // Una venta de la copia de la tienda no es del dueño.
        stock.changeStock("sheol", 2, -1, StockOperationsService.Reason.SALE);

        var owner = service.owners().get(0);
        assertThat(owner.name()).isEqualTo("Juan");
        assertThat(owner.sold()).isEqualTo(1);
        assertThat(owner.inStock()).isEqualTo(1);
        assertThat(owner.owed()).isEqualTo(7000);
    }

    @Test
    void payingInCashLeavesTheDrawerAndClearsTheDebt() throws Exception {
        Consignment item = receiveTwo();
        var copy = store.card(item.getCardId());
        stock.changeStock(copy.getId(), copy.getRowIndex(), -1, StockOperationsService.Reason.SALE);

        double paid = service.pay("juan", ConsignmentService.Payout.CASH);

        assertThat(paid).isEqualTo(7000);
        CashRegisterEntry entry = store.cash.get(store.cash.size() - 1);
        assertThat(entry.isBuy()).isTrue();
        assertThat(entry.drawerAmount()).isEqualTo(-7000);
        assertThat(service.owners().get(0).owed()).isZero();
        assertThatThrownBy(() -> service.pay("Juan", ConsignmentService.Payout.CASH))
                .hasMessageContaining("No le debés nada");
    }

    @Test
    void payingWithCreditAddsToTheOwnersBalance() throws Exception {
        Consignment item = receiveTwo();
        var copy = store.card(item.getCardId());
        stock.changeStock(copy.getId(), copy.getRowIndex(), -2, StockOperationsService.Reason.SALE);

        service.pay("Juan", ConsignmentService.Payout.CREDIT);

        assertThat(credit.balance("Juan")).isEqualTo(14000);
        // Todo vendido y pagado: ya no aparece.
        assertThat(service.owners()).isEmpty();
    }

    @Test
    void givingBackRemovesTheUnsoldStock() throws Exception {
        Consignment item = receiveTwo();

        int units = service.giveBack(item.getId());

        assertThat(units).isEqualTo(2);
        assertThat(store.card(item.getCardId()).getQuantity()).isEqualTo("0");
        assertThat(store.consignments.get(0).isReturned()).isTrue();
        assertThat(service.owners()).isEmpty();
    }

    @Test
    void needsOwnerAndValidShare() {
        var line = List.of(new ConsignmentService.Line(store.card("sheol"), 1));
        assertThatThrownBy(() -> service.receive(" ", "", 70, line)).isInstanceOf(StockOperationsService.StockOperationException.class);
        assertThatThrownBy(() -> service.receive("Juan", "", 0, line)).isInstanceOf(StockOperationsService.StockOperationException.class);
    }
}
