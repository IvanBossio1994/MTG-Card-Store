package com.tcg.bot.service;

import com.tcg.bot.model.Reservation;
import com.tcg.bot.service.ReservationService.Item;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import com.tcg.bot.service.StockOperationsServiceTest.FakeStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static com.tcg.bot.service.StockOperationsServiceTest.card;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Buenos_Aires");
    private static final Instant NOW = Instant.parse("2026-11-03T15:00:00Z");

    private FakeStore store;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        store.cards.add(card("sol", 2, "Sol Ring", 3, "1500"));
        store.cards.add(card("cs", 3, "Counterspell", 1, "800"));
    }

    private ReservationService serviceAt(Instant instant) {
        var stock = new StockOperationsService(store, Clock.fixed(instant, ZONE));
        return new ReservationService(store, stock);
    }

    @Test
    void reservingTakesCardsOutOfAvailableStockWithoutTouchingCash() throws Exception {
        String id = serviceAt(NOW).create("Juan", "@juan", 7,
                List.of(new Item("sol", 2), new Item("cs", 1)));

        assertThat(store.card("sol").getQuantity()).isEqualTo("1");
        assertThat(store.card("cs").getQuantity()).isEqualTo("0");
        assertThat(store.cash).isEmpty();
        assertThat(store.reservations).hasSize(2)
                .allSatisfy(line -> {
                    assertThat(line.getReservationId()).isEqualTo(id);
                    assertThat(line.getStatus()).isEqualTo(Reservation.STATUS_ACTIVE);
                    assertThat(line.getExpiresOn()).isEqualTo("2026-11-10");
                });
    }

    @Test
    void notEnoughStockReservesNothing() {
        assertThatThrownBy(() -> serviceAt(NOW).create("Juan", "", 7,
                List.of(new Item("sol", 1), new Item("cs", 2))))
                .isInstanceOf(StockOperationException.class)
                .hasMessageContaining("Counterspell");

        assertThat(store.card("sol").getQuantity()).isEqualTo("3");
        assertThat(store.reservations).isEmpty();
    }

    @Test
    void cannotReserveCardWithoutPrice() {
        store.cards.add(card("np", 4, "Sin Precio", 2, ""));

        assertThatThrownBy(() -> serviceAt(NOW).create("Juan", "", 7, List.of(new Item("np", 1))))
                .isInstanceOf(StockOperationException.class)
                .hasMessageContaining("precio");
        assertThat(store.card("np").getQuantity()).isEqualTo("2");
    }

    @Test
    void pickUpRegistersSalesAndKeepsStockDown() throws Exception {
        var service = serviceAt(NOW);
        String id = service.create("Juan", "", 7, List.of(new Item("sol", 2), new Item("cs", 1)));

        var order = service.pickUp(id);

        assertThat(order.formattedTotal()).isEqualTo("3.800");
        assertThat(store.cash).hasSize(2).allSatisfy(entry -> assertThat(entry.isSale()).isTrue());
        assertThat(store.cash.stream().mapToDouble(e -> e.signedTotal()).sum()).isEqualTo(3800);
        assertThat(store.card("sol").getQuantity()).isEqualTo("1");
        assertThat(store.reservations).allSatisfy(line ->
                assertThat(line.getStatus()).isEqualTo(Reservation.STATUS_PICKED_UP));
    }

    @Test
    void retryingAFailedPickUpDoesNotChargeTwice() throws Exception {
        var service = serviceAt(NOW);
        String id = service.create("Juan", "", 7, List.of(new Item("sol", 2), new Item("cs", 1)));

        store.failStatuses = true;
        assertThatThrownBy(() -> service.pickUp(id)).isInstanceOf(IllegalStateException.class);
        assertThat(store.cash).hasSize(2);

        // En el Sheet la reserva quedo activa porque no se pudo guardar el estado.
        store.reservations.forEach(line -> line.setStatus(Reservation.STATUS_ACTIVE));
        store.failStatuses = false;
        service.pickUp(id);

        assertThat(store.cash).hasSize(2);
        assertThat(store.reservations).allSatisfy(line ->
                assertThat(line.getStatus()).isEqualTo(Reservation.STATUS_PICKED_UP));
    }

    @Test
    void cancelReturnsCardsToStock() throws Exception {
        var service = serviceAt(NOW);
        String id = service.create("Juan", "", 7, List.of(new Item("sol", 2)));

        service.cancel(id);

        assertThat(store.card("sol").getQuantity()).isEqualTo("3");
        assertThat(store.cash).isEmpty();
        assertThatThrownBy(() -> service.cancel(id)).isInstanceOf(StockOperationException.class);
        assertThat(store.card("sol").getQuantity()).isEqualTo("3");
    }

    @Test
    void expiredReservationsGoBackToStock() throws Exception {
        String id = serviceAt(NOW).create("Juan", "", 2, List.of(new Item("sol", 2)));

        assertThat(serviceAt(NOW.plus(Duration.ofDays(2))).releaseExpired()).isZero();
        assertThat(store.card("sol").getQuantity()).isEqualTo("1");

        assertThat(serviceAt(NOW.plus(Duration.ofDays(3))).releaseExpired()).isEqualTo(1);
        assertThat(store.card("sol").getQuantity()).isEqualTo("3");
        assertThat(store.reservations.get(0).getStatus()).isEqualTo(Reservation.STATUS_EXPIRED);

        assertThatThrownBy(() -> serviceAt(NOW.plus(Duration.ofDays(3))).pickUp(id))
                .isInstanceOf(StockOperationException.class);
    }

    @Test
    void activeOrdersAreListedFirst() throws Exception {
        var service = serviceAt(NOW);
        String old = service.create("Ana", "", 7, List.of(new Item("cs", 1)));
        service.cancel(old);
        String active = service.create("Juan", "", 7, List.of(new Item("sol", 1)));

        assertThat(service.orders()).extracting(ReservationService.Order::reservationId)
                .containsExactly(active, old);
    }
}
