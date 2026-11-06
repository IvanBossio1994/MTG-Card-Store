package com.tcg.bot.service;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.Reservation;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import com.tcg.bot.util.Amounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reservas (pedidos de clientes), guardadas en la hoja "Reservas" del Sheet.
 * <ul>
 *     <li>Reservar saca las cartas del stock disponible, asi nadie mas las vende. No toca la caja.</li>
 *     <li>Retirar cobra: registra cada linea como VENTA en la caja.</li>
 *     <li>Cancelar, o que venza sin retirarse, devuelve las cartas al stock.</li>
 * </ul>
 */
@Service
public class ReservationService {

    public static final int DEFAULT_DAYS = 7;

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final InventoryStore store;
    private final StockOperationsService stockOperations;

    public ReservationService(InventoryStore store, StockOperationsService stockOperations) {
        this.store = store;
        this.stockOperations = stockOperations;
    }

    public record Item(String cardId, int quantity) {
    }

    /** Un pedido completo: todas las lineas que comparten ID de reserva. */
    public record Order(
            String reservationId,
            String customer,
            String contact,
            String date,
            String formattedDate,
            String expiresOn,
            String formattedExpiresOn,
            String status,
            List<Reservation> lines,
            int units,
            String formattedTotal
    ) {
        public boolean isActive() {
            return Reservation.STATUS_ACTIVE.equalsIgnoreCase(status);
        }
    }

    public String create(String customer, String contact, int days, List<Item> items) throws Exception {
        if (customer == null || customer.isBlank()) {
            throw new StockOperationException(I18n.t("err.customer.required"));
        }

        if (items == null || items.isEmpty()) {
            throw new StockOperationException(I18n.t("err.order.empty"));
        }

        synchronized (StockOperationsService.LOCK) {
            List<InventoryCard> cards = store.getAllStockCards();
            Map<String, Integer> requested = new LinkedHashMap<>();
            for (Item item : items) {
                if (item.cardId() == null || item.cardId().isBlank() || item.quantity() <= 0) {
                    continue;
                }
                requested.merge(item.cardId(), item.quantity(), Integer::sum);
            }

            if (requested.isEmpty()) {
                throw new StockOperationException(I18n.t("err.order.empty"));
            }

            // Primero se valida todo; si falta stock de alguna carta no se reserva nada.
            List<InventoryCard> targets = new ArrayList<>();
            for (var entry : requested.entrySet()) {
                InventoryCard card = cards.stream()
                        .filter(candidate -> entry.getKey().equals(candidate.getId()))
                        .findFirst()
                        .orElseThrow(() -> new StockOperationException(
                                I18n.t("err.orderCardGone")));

                if (Amounts.parse(card.getLocalPrice()) <= 0) {
                    throw new StockOperationException(card.getName()
                            + " no tiene precio local. Sincronizá precios antes de reservarla.");
                }

                if (StockOperationsService.quantity(card) < entry.getValue()) {
                    throw new StockOperationException(I18n.t("err.notEnoughStock", card.getName(), StockOperationsService.quantity(card), entry.getValue()));
                }

                targets.add(card);
            }

            LocalDateTime now = LocalDateTime.now(stockOperations.clock);
            String reservationId = "R" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            String expiresOn = now.toLocalDate().plusDays(Math.max(days, 0)).format(DATE_FORMAT);
            List<Reservation> lines = new ArrayList<>();

            for (InventoryCard card : targets) {
                int units = requested.get(card.getId());
                Reservation line = new Reservation();
                line.setDate(now.format(DATE_FORMAT));
                line.setTime(now.format(TIME_FORMAT));
                line.setReservationId(reservationId);
                line.setCustomer(customer.trim());
                line.setContact(contact == null ? "" : contact.trim());
                line.setCardId(card.getId());
                line.setName(card.getName());
                line.setSetName(card.getSetName());
                line.setSetCode(card.getSetCode());
                line.setCollectorNumber(card.getCollectorNumber());
                line.setPrinting(card.getPrinting());
                line.setQuantity(String.valueOf(units));
                line.setUnitPrice(String.valueOf(Amounts.parse(card.getLocalPrice())));
                line.setExpiresOn(expiresOn);
                line.setStatus(Reservation.STATUS_ACTIVE);
                line.setUpdatedAt(now.format(STAMP_FORMAT));
                lines.add(line);
            }

            // La reserva queda anotada antes de tocar el stock: si algo falla despues, se ve en el Sheet.
            store.appendReservations(lines);

            for (InventoryCard card : targets) {
                int units = requested.get(card.getId());
                int previous = StockOperationsService.quantity(card);
                int next = previous - units;
                StockOperationsService.setQuantity(card, next);
                store.updateAnyStock(card);
                stockOperations.appendMovementQuietly(stockOperations.movement(
                        now, -units, card, previous, next, StockOperationsService.SOURCE_RESERVED + " (" + customer.trim() + ")"));
            }

            return reservationId;
        }
    }

    /** El cliente retiro y pago: cada linea pasa a la caja como venta. */
    public Order pickUp(String reservationId) throws Exception {
        synchronized (StockOperationsService.LOCK) {
            List<Reservation> lines = activeLines(reservationId);
            LocalDateTime now = LocalDateTime.now(stockOperations.clock);

            // Cada linea tiene un ID de venta fijo. Si un retiro anterior se corto a mitad (por ejemplo,
            // se cayo internet despues de cobrar), al reintentar no se vuelve a cobrar lo que ya esta en la caja.
            java.util.Set<String> alreadyCharged = new java.util.HashSet<>();
            for (CashRegisterEntry entry : store.getCashRegisterEntries()) {
                if (entry.getSaleId() != null) {
                    alreadyCharged.add(entry.getSaleId());
                }
            }

            // Retirar es cobrar: cuenta como una operación del plan gratis (si no, reservar y retirar lo esquivaría).
            if (lines.stream().anyMatch(line -> !alreadyCharged.contains(saleIdFor(line)))) {
                stockOperations.requireFreeOperation();
            }

            for (Reservation line : lines) {
                String saleId = saleIdFor(line);
                if (alreadyCharged.contains(saleId)) {
                    continue;
                }
                InventoryCard snapshot = cardFromLine(line);
                CashRegisterEntry sale = stockOperations.cashEntry(
                        now, snapshot, CashRegisterEntry.TYPE_SALE, line.units(), Amounts.parse(line.getUnitPrice()));
                sale.setSaleId(saleId);
                store.appendCashEntry(sale);
            }

            markAll(lines, Reservation.STATUS_PICKED_UP, now);
            return toOrders(lines).get(0);
        }
    }

    public Order cancel(String reservationId) throws Exception {
        synchronized (StockOperationsService.LOCK) {
            List<Reservation> lines = activeLines(reservationId);
            release(lines, Reservation.STATUS_CANCELLED);
            return toOrders(lines).get(0);
        }
    }

    /** Libera las reservas vencidas. Corre cada media hora y cada vez que se abre la pantalla de reservas. */
    @Scheduled(initialDelay = 60_000, fixedDelay = 30 * 60_000)
    public void releaseExpiredQuietly() {
        try {
            releaseExpired();
        } catch (Exception e) {
            log.debug("No se pudieron revisar reservas vencidas: {}", e.getMessage());
        }
    }

    public int releaseExpired() throws Exception {
        synchronized (StockOperationsService.LOCK) {
            LocalDate today = LocalDate.now(stockOperations.clock);
            List<Reservation> overdue = store.getReservations().stream()
                    .filter(line -> line.isOverdue(today))
                    .toList();

            if (overdue.isEmpty()) {
                return 0;
            }

            release(overdue, Reservation.STATUS_EXPIRED);
            return (int) overdue.stream().map(Reservation::getReservationId).distinct().count();
        }
    }

    public List<Order> orders() throws Exception {
        List<Order> orders = toOrders(store.getReservations());
        List<Order> sorted = new ArrayList<>(orders);
        // Activas primero, despues las mas nuevas.
        sorted.sort((first, second) -> {
            if (first.isActive() != second.isActive()) {
                return first.isActive() ? -1 : 1;
            }
            return (second.date() + second.reservationId()).compareTo(first.date() + first.reservationId());
        });
        return sorted;
    }

    private void release(List<Reservation> lines, String status) throws Exception {
        LocalDateTime now = LocalDateTime.now(stockOperations.clock);
        List<InventoryCard> cards = store.getAllStockCards();

        // Primero se marca la reserva: si se cae a mitad, no se devuelve stock dos veces.
        markAll(lines, status, now);

        for (Reservation line : lines) {
            InventoryCard card = cards.stream()
                    .filter(candidate -> line.getCardId() != null && line.getCardId().equals(candidate.getId()))
                    .findFirst()
                    .orElse(null);

            if (card == null) {
                log.warn("Reserva {}: no se encontró {} para devolver el stock.", line.getReservationId(), line.getName());
                continue;
            }

            int previous = StockOperationsService.quantity(card);
            int next = previous + line.units();
            StockOperationsService.setQuantity(card, next);
            store.updateAnyStock(card);
            stockOperations.appendMovementQuietly(stockOperations.movement(
                    now, line.units(), card, previous, next,
                    StockOperationsService.SOURCE_RESERVATION_RELEASED + " (" + line.getCustomer() + ")"));
        }
    }

    private void markAll(List<Reservation> lines, String status, LocalDateTime now) throws Exception {
        for (Reservation line : lines) {
            line.setStatus(status);
            line.setUpdatedAt(now.format(STAMP_FORMAT));
        }
        store.updateReservationStatuses(lines);
    }

    private List<Reservation> activeLines(String reservationId) throws Exception {
        List<Reservation> lines = store.getReservations().stream()
                .filter(line -> reservationId != null && reservationId.equals(line.getReservationId()))
                .toList();

        if (lines.isEmpty()) {
            throw new StockOperationException(I18n.t("res.err.notFound"));
        }

        List<Reservation> active = lines.stream().filter(Reservation::isActive).toList();
        if (active.isEmpty()) {
            throw new StockOperationException(I18n.t("res.err.already", statusLabel(lines.get(0).getStatus())));
        }

        return active;
    }

    static String saleIdFor(Reservation line) {
        return "V-" + line.getReservationId() + "-" + line.getCardId();
    }

    private static InventoryCard cardFromLine(Reservation line) {
        InventoryCard card = new InventoryCard();
        card.setId(line.getCardId());
        card.setName(line.getName());
        card.setSetName(line.getSetName());
        card.setSetCode(line.getSetCode());
        card.setCollectorNumber(line.getCollectorNumber());
        card.setPrinting(line.getPrinting());
        return card;
    }

    static List<Order> toOrders(List<Reservation> lines) {
        Map<String, List<Reservation>> byId = new LinkedHashMap<>();
        for (Reservation line : lines) {
            byId.computeIfAbsent(line.getReservationId(), key -> new ArrayList<>()).add(line);
        }

        List<Order> orders = new ArrayList<>();
        for (var entry : byId.entrySet()) {
            List<Reservation> orderLines = entry.getValue();
            Reservation first = orderLines.get(0);
            boolean anyActive = orderLines.stream().anyMatch(Reservation::isActive);
            double total = orderLines.stream().mapToDouble(Reservation::total).sum();
            orders.add(new Order(
                    entry.getKey(),
                    first.getCustomer(),
                    first.getContact(),
                    first.getDate(),
                    first.getFormattedDate(),
                    first.getExpiresOn(),
                    first.getFormattedExpiresOn(),
                    anyActive ? Reservation.STATUS_ACTIVE : first.getStatus(),
                    orderLines,
                    orderLines.stream().mapToInt(Reservation::units).sum(),
                    com.tcg.bot.i18n.Money.number(total)
            ));
        }

        return orders;
    }

    private static String statusLabel(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
        String label = I18n.t("res.status." + value);
        return (label.startsWith("res.status.") ? value : label).toLowerCase(I18n.locale());
    }
}
