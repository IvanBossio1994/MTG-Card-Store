package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.InventoryMovement;
import com.tcg.bot.model.Reservation;
import com.tcg.bot.service.StockOperationsService.Reason;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockOperationsServiceTest {

    private FakeStore store;
    private StockOperationsService service;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T15:00:00Z"), ZoneId.of("America/Buenos_Aires"));
        service = new StockOperationsService(store, clock);
    }

    @Test
    void saleLowersStockAndRegistersCash() throws Exception {
        store.cards.add(card("c1", 2, "Sol Ring", 3, "1500"));

        var result = service.changeStock("c1", 2, -1, Reason.SALE);

        assertThat(result.stockQuantity()).isEqualTo(2);
        assertThat(store.card("c1").getQuantity()).isEqualTo("2");
        assertThat(store.cash).hasSize(1);
        CashRegisterEntry sale = store.cash.get(0);
        assertThat(sale.isSale()).isTrue();
        assertThat(sale.signedTotal()).isEqualTo(1500);
        assertThat(sale.getSaleId()).isNotBlank();
        assertThat(sale.getCardId()).isEqualTo("c1");
        assertThat(sale.getDate()).isEqualTo("2026-11-03");
        assertThat(store.movements).extracting(InventoryMovement::getSource)
                .containsExactly(StockOperationsService.SOURCE_SOLD);
    }

    @Test
    void saleReadsThousandsSeparatedPrice() throws Exception {
        // Antes "1.500" se leia como 1,5 y la venta quedaba en $2.
        store.cards.add(card("c1", 2, "Sol Ring", 1, "1.500"));

        service.changeStock("c1", 2, -1, Reason.SALE);

        assertThat(store.cash.get(0).signedTotal()).isEqualTo(1500);
    }

    @Test
    void adjustmentDoesNotTouchCash() throws Exception {
        store.cards.add(card("c1", 2, "Sol Ring", 3, "1500"));

        var result = service.changeStock("c1", 2, -1, Reason.ADJUST);

        assertThat(result.stockQuantity()).isEqualTo(2);
        assertThat(store.cash).isEmpty();
        assertThat(store.movements).extracting(InventoryMovement::getSource)
                .containsExactly(StockOperationsService.SOURCE_ADJUSTED);
    }

    @Test
    void addingStockIsNeverASale() throws Exception {
        store.cards.add(card("c1", 2, "Sol Ring", 0, "1500"));

        var result = service.changeStock("c1", 2, 1, Reason.SALE);

        assertThat(result.stockQuantity()).isEqualTo(1);
        assertThat(result.action()).isEqualTo(StockOperationsService.ACTION_IN_STOCK);
        assertThat(store.cash).isEmpty();
    }

    @Test
    void findsCardByIdEvenIfRowsMoved() throws Exception {
        // El Sheet se ordeno: el cliente todavia cree que Sol Ring esta en la fila 2.
        store.cards.add(card("other", 2, "Counterspell", 5, "800"));
        store.cards.add(card("c1", 3, "Sol Ring", 1, "1500"));

        service.changeStock("c1", 2, -1, Reason.SALE);

        assertThat(store.card("c1").getQuantity()).isEqualTo("0");
        assertThat(store.card("other").getQuantity()).isEqualTo("5");
        assertThat(store.cash.get(0).getName()).isEqualTo("Sol Ring");
    }

    @Test
    void unknownIdFailsInsteadOfTouchingAnotherCard() {
        store.cards.add(card("other", 2, "Counterspell", 5, "800"));

        assertThatThrownBy(() -> service.changeStock("gone", 2, -1, Reason.SALE))
                .isInstanceOf(StockOperationException.class);
        assertThat(store.card("other").getQuantity()).isEqualTo("5");
        assertThat(store.cash).isEmpty();
    }

    @Test
    void cannotSellWithoutStock() {
        store.cards.add(card("c1", 2, "Sol Ring", 0, "1500"));

        assertThatThrownBy(() -> service.changeStock("c1", 2, -1, Reason.SALE))
                .isInstanceOf(StockOperationException.class);
        assertThat(store.cash).isEmpty();
    }

    @Test
    void cannotSellWithoutPrice() {
        store.cards.add(card("c1", 2, "Sol Ring", 1, ""));

        assertThatThrownBy(() -> service.changeStock("c1", 2, -1, Reason.SALE))
                .isInstanceOf(StockOperationException.class)
                .hasMessageContaining("precio");
        assertThat(store.card("c1").getQuantity()).isEqualTo("1");
    }

    @Test
    void stockIsRestoredWhenCashFails() {
        store.cards.add(card("c1", 2, "Sol Ring", 3, "1500"));
        store.failCash = true;

        assertThatThrownBy(() -> service.changeStock("c1", 2, -1, Reason.SALE));

        assertThat(store.card("c1").getQuantity()).isEqualTo("3");
    }

    @Test
    void movementFailureDoesNotUndoSale() throws Exception {
        store.cards.add(card("c1", 2, "Sol Ring", 3, "1500"));
        store.failMovements = true;

        service.changeStock("c1", 2, -1, Reason.SALE);

        assertThat(store.card("c1").getQuantity()).isEqualTo("2");
        assertThat(store.cash).hasSize(1);
    }

    @Test
    void voidSaleSubtractsCashAndRestoresStock() throws Exception {
        store.cards.add(card("c1", 2, "Sol Ring", 3, "1500"));
        var sale = service.changeStock("c1", 2, -2, Reason.SALE);

        var result = service.voidSale(sale.saleId());

        assertThat(result.stockRestored()).isTrue();
        assertThat(store.card("c1").getQuantity()).isEqualTo("3");
        assertThat(store.cash).hasSize(2);
        assertThat(store.cash.get(1).isVoid()).isTrue();
        assertThat(store.cash.get(1).getVoidsSaleId()).isEqualTo(sale.saleId());
        assertThat(store.cash.stream().mapToDouble(CashRegisterEntry::signedTotal).sum()).isZero();
        assertThat(store.cash.stream().mapToInt(CashRegisterEntry::signedQuantity).sum()).isZero();
        assertThat(store.movements).extracting(InventoryMovement::getSource)
                .containsExactly(StockOperationsService.SOURCE_SOLD, StockOperationsService.SOURCE_VOIDED);
    }

    @Test
    void cannotVoidTwice() throws Exception {
        store.cards.add(card("c1", 2, "Sol Ring", 3, "1500"));
        var sale = service.changeStock("c1", 2, -1, Reason.SALE);
        service.voidSale(sale.saleId());

        assertThatThrownBy(() -> service.voidSale(sale.saleId()))
                .isInstanceOf(StockOperationException.class);
        assertThat(store.card("c1").getQuantity()).isEqualTo("3");
    }

    @Test
    void voidFallsBackToCardDataForOldSales() throws Exception {
        store.cards.add(card("c1", 2, "Sol Ring", 0, "1500"));
        CashRegisterEntry oldSale = new CashRegisterEntry();
        oldSale.setType(CashRegisterEntry.TYPE_SALE);
        oldSale.setSaleId("V-old");
        oldSale.setName("Sol Ring");
        oldSale.setSetCode("CMM");
        oldSale.setCollectorNumber("410");
        oldSale.setPrinting("nonfoil");
        oldSale.setLocalPrice("1500");
        oldSale.setQuantity("1");
        oldSale.setTotal("1500");
        store.cash.add(oldSale);

        service.voidSale("V-old");

        assertThat(store.card("c1").getQuantity()).isEqualTo("1");
    }

    static InventoryCard card(String id, int row, String name, int quantity, String price) {
        InventoryCard card = new InventoryCard();
        card.setId(id);
        card.setRowIndex(row);
        card.setName(name);
        card.setSetCode("CMM");
        card.setSetName("Commander Masters");
        card.setCollectorNumber("410");
        card.setPrinting("nonfoil");
        card.setQuantity(String.valueOf(quantity));
        card.setLocalPrice(price);
        return card;
    }

    /** Sheet en memoria. Devuelve copias, como haria una lectura real. */
    static class FakeStore implements InventoryStore {
        final List<InventoryCard> cards = new ArrayList<>();
        final List<InventoryMovement> movements = new ArrayList<>();
        final List<CashRegisterEntry> cash = new ArrayList<>();
        final List<Reservation> reservations = new ArrayList<>();
        final List<com.tcg.bot.model.CreditEntry> credits = new ArrayList<>();
        final java.util.Map<com.tcg.bot.model.Game, List<com.tcg.bot.model.GameCard>> games = new java.util.EnumMap<>(com.tcg.bot.model.Game.class);
        boolean failCash;
        boolean failMovements;
        boolean failStatuses;

        InventoryCard card(String id) {
            return cards.stream().filter(card -> id.equals(card.getId())).findFirst().orElseThrow();
        }

        @Override
        public List<InventoryCard> getInventoryCards() {
            List<InventoryCard> copies = new ArrayList<>();
            for (InventoryCard card : cards) {
                InventoryCard copy = StockOperationsServiceTest.card(card.getId(), card.getRowIndex(), card.getName(),
                        Integer.parseInt(card.getQuantity()), card.getLocalPrice());
                copy.setSetCode(card.getSetCode());
                copy.setCollectorNumber(card.getCollectorNumber());
                copy.setPrinting(card.getPrinting());
                copies.add(copy);
            }
            return copies;
        }

        @Override
        public void updateLocalPrice(int rowIndex, String localPrice) {
            cards.stream()
                    .filter(card -> card.getRowIndex() == rowIndex)
                    .findFirst()
                    .orElseThrow()
                    .setLocalPrice(localPrice);
        }

        @Override
        public void updateStockState(int rowIndex, InventoryCard updated) {
            InventoryCard target = cards.stream()
                    .filter(card -> card.getRowIndex() == rowIndex)
                    .findFirst()
                    .orElseThrow();
            target.setQuantity(updated.getQuantity());
            target.setAction(updated.getAction());
        }

        @Override
        public void appendMovement(InventoryMovement movement) {
            if (failMovements) {
                throw new IllegalStateException("Sheets caido");
            }
            movements.add(movement);
        }

        @Override
        public List<CashRegisterEntry> getCashRegisterEntries() {
            return List.copyOf(cash);
        }

        @Override
        public List<Reservation> getReservations() {
            return List.copyOf(reservations);
        }

        @Override
        public void appendReservations(List<Reservation> lines) {
            for (Reservation line : lines) {
                line.setRowIndex(reservations.size() + 2);
                reservations.add(line);
            }
        }

        @Override
        public void updateReservationStatuses(List<Reservation> lines) {
            if (failStatuses) {
                throw new IllegalStateException("sin conexión");
            }
            for (Reservation line : lines) {
                Reservation stored = reservations.get(line.getRowIndex() - 2);
                stored.setStatus(line.getStatus());
                stored.setUpdatedAt(line.getUpdatedAt());
            }
        }

        @Override
        public List<com.tcg.bot.model.GameCard> getGameCards(com.tcg.bot.model.Game game) {
            List<com.tcg.bot.model.GameCard> copies = new ArrayList<>();
            for (var card : games.getOrDefault(game, List.of())) {
                copies.add(com.tcg.bot.model.GameCard.fromRow(card.toRow().stream().map(String::valueOf).toList(), card.getRowIndex()));
            }
            return copies;
        }

        @Override
        public int appendGameCard(com.tcg.bot.model.Game game, com.tcg.bot.model.GameCard card) {
            var list = games.computeIfAbsent(game, key -> new ArrayList<>());
            int row = list.size() + 2;
            card.setRowIndex(row);
            list.add(card);
            return row;
        }

        @Override
        public void updateGameCard(com.tcg.bot.model.Game game, com.tcg.bot.model.GameCard card) {
            var list = games.get(game);
            list.set(card.getRowIndex() - 2, card);
        }

        @Override
        public List<com.tcg.bot.model.CreditEntry> getCreditEntries() {
            return List.copyOf(credits);
        }

        @Override
        public void appendCreditEntry(com.tcg.bot.model.CreditEntry entry) {
            credits.add(entry);
        }

        @Override
        public int appendInventoryCard(InventoryCard card) {
            int row = cards.stream().mapToInt(InventoryCard::getRowIndex).max().orElse(1) + 1;
            card.setRowIndex(row);
            cards.add(card);
            return row;
        }

        @Override
        public void appendCashEntry(CashRegisterEntry entry) throws Exception {
            if (failCash) {
                throw new Exception("Sheets caido");
            }
            cash.add(entry);
        }
    }
}
