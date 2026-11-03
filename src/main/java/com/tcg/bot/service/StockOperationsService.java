package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.InventoryMovement;
import com.tcg.bot.util.Amounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Operaciones de stock y caja. Reglas:
 * <ul>
 *     <li>Solo una VENTA anota plata en la caja. Un ajuste de stock (corregir un error de carga,
 *     una carta dañada, un regalo) mueve stock pero no toca la caja.</li>
 *     <li>Una venta no se borra: se anula con una fila ANULACION que resta en la caja y devuelve el stock.</li>
 *     <li>La carta se busca por su ID estable; el numero de fila es solo una pista, porque el Sheet se puede
 *     ordenar o editar a mano.</li>
 *     <li>Si falla el registro en caja despues de bajar el stock, se devuelve el stock.</li>
 * </ul>
 */
@Service
public class StockOperationsService {

    public static final String ACTION_IN_STOCK = "CON STOCK";
    public static final String ACTION_OUT_OF_STOCK = "SIN STOCK";

    public static final String SOURCE_ADDED = "Agregado al stock";
    public static final String SOURCE_SOLD = "Unidad vendida";
    public static final String SOURCE_ADJUSTED = "Ajuste de stock";
    public static final String SOURCE_VOIDED = "Venta anulada";
    public static final String SOURCE_RESERVED = "Reservada";
    public static final String SOURCE_RESERVATION_RELEASED = "Reserva liberada";

    /** Todas las escrituras de stock/caja/reservas pasan por aca para no pisarse entre si. */
    static final Object LOCK = new Object();

    private static final Logger log = LoggerFactory.getLogger(StockOperationsService.class);
    private static final ZoneId APP_ZONE = ZoneId.of("America/Buenos_Aires");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public enum Reason {
        /** Entra o sale stock sin plata de por medio. */
        ADJUST,
        /** Sale stock porque se vendio: registra en caja. */
        SALE;

        public static Reason from(String value) {
            return "adjust".equalsIgnoreCase(value) || "ajuste".equalsIgnoreCase(value) ? ADJUST : SALE;
        }
    }

    private final InventoryStore store;
    final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public StockOperationsService(InventoryStore store) {
        this(store, Clock.system(APP_ZONE));
    }

    StockOperationsService(InventoryStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /**
     * Suma o resta unidades de una carta. Con {@code change < 0} y motivo SALE, registra la venta en caja.
     * Sumar unidades nunca es una venta.
     */
    public StockChange changeStock(String cardId, int rowIndexHint, int change, Reason reason) throws Exception {
        synchronized (LOCK) {
            return changeStockLocked(cardId, rowIndexHint, change, reason);
        }
    }

    private StockChange changeStockLocked(String cardId, int rowIndexHint, int change, Reason reason) throws Exception {
        if (change == 0) {
            throw new StockOperationException("No hay cambios para aplicar.");
        }

        InventoryCard card = findCard(cardId, rowIndexHint);
        int previous = quantity(card);
        int next = Math.max(previous + change, 0);
        int applied = Math.abs(next - previous);
        boolean isSale = change < 0 && reason == Reason.SALE;

        if (applied == 0) {
            throw new StockOperationException("No hay unidades de " + card.getName() + " para descontar.");
        }

        double unitPrice = Amounts.parse(card.getLocalPrice());
        if (isSale && unitPrice <= 0) {
            throw new StockOperationException(
                    "La carta " + card.getName() + " no tiene precio local. Sincronizá precios o usá \"Ajuste\" si no es una venta."
            );
        }

        setQuantity(card, next);
        store.updateStockState(card.getRowIndex(), card);

        LocalDateTime now = LocalDateTime.now(clock);
        String saleId = null;

        if (isSale) {
            saleId = newSaleId();
            CashRegisterEntry sale = cashEntry(now, card, CashRegisterEntry.TYPE_SALE, applied, unitPrice);
            sale.setSaleId(saleId);

            try {
                store.appendCashEntry(sale);
            } catch (Exception e) {
                rollbackStock(card, previous);
                throw e;
            }
        }

        String source = change > 0 ? SOURCE_ADDED : isSale ? SOURCE_SOLD : SOURCE_ADJUSTED;
        appendMovementQuietly(movement(now, change > 0 ? applied : -applied, card, previous, next, source));

        return new StockChange(card.getId(), card.getRowIndex(), next, actionFor(next), saleId, source);
    }

    /**
     * Anula una venta: agrega una fila ANULACION que resta el total en caja y devuelve las unidades al stock.
     */
    public VoidResult voidSale(String saleId) throws Exception {
        synchronized (LOCK) {
            return voidSaleLocked(saleId);
        }
    }

    private VoidResult voidSaleLocked(String saleId) throws Exception {
        if (saleId == null || saleId.isBlank()) {
            throw new StockOperationException("Falta indicar qué venta anular.");
        }

        List<CashRegisterEntry> entries = store.getCashRegisterEntries();
        CashRegisterEntry sale = entries.stream()
                .filter(entry -> entry.isSale() && saleId.equals(entry.getSaleId()))
                .findFirst()
                .orElseThrow(() -> new StockOperationException("No se encontró la venta a anular."));

        boolean alreadyVoided = entries.stream()
                .anyMatch(entry -> entry.isVoid() && saleId.equals(entry.getVoidsSaleId()));
        if (alreadyVoided || sale.isVoided()) {
            throw new StockOperationException("Esa venta ya estaba anulada.");
        }

        int units = Math.abs(sale.signedQuantity());
        double unitPrice = Amounts.parse(sale.getLocalPrice());
        LocalDateTime now = LocalDateTime.now(clock);

        CashRegisterEntry voidEntry = new CashRegisterEntry();
        voidEntry.setDate(now.format(DATE_FORMAT));
        voidEntry.setTime(now.format(TIME_FORMAT));
        voidEntry.setType(CashRegisterEntry.TYPE_VOID);
        voidEntry.setName(sale.getName());
        voidEntry.setSetName(sale.getSetName());
        voidEntry.setSetCode(sale.getSetCode());
        voidEntry.setCollectorNumber(sale.getCollectorNumber());
        voidEntry.setPrinting(sale.getPrinting());
        voidEntry.setLocalPrice(sale.getLocalPrice());
        voidEntry.setQuantity(String.valueOf(units));
        voidEntry.setTotal(String.valueOf(Math.abs(sale.signedTotal())));
        voidEntry.setSaleId(newSaleId());
        voidEntry.setVoidsSaleId(saleId);
        voidEntry.setCardId(sale.getCardId());

        store.appendCashEntry(voidEntry);

        InventoryCard card = findCardForSale(sale);
        if (card == null) {
            log.warn("Venta {} anulada, pero no se encontró la carta para devolver el stock.", saleId);
            return new VoidResult(saleId, false, unitPrice * units);
        }

        int previous = quantity(card);
        int next = previous + units;
        setQuantity(card, next);
        store.updateStockState(card.getRowIndex(), card);
        appendMovementQuietly(movement(now, units, card, previous, next, SOURCE_VOIDED));

        return new VoidResult(saleId, true, unitPrice * units);
    }

    private InventoryCard findCard(String cardId, int rowIndexHint) throws Exception {
        List<InventoryCard> cards = store.getInventoryCards();

        if (cardId != null && !cardId.isBlank()) {
            return cards.stream()
                    .filter(card -> cardId.equals(card.getId()))
                    .findFirst()
                    .orElseThrow(() -> new StockOperationException(
                            "No se encontró la carta en el inventario. Volvé a buscarla: puede que el Sheet haya cambiado."));
        }

        // Sin ID (Sheet viejo sin la columna): se usa la fila, como antes.
        return cards.stream()
                .filter(card -> card.getRowIndex() == rowIndexHint)
                .findFirst()
                .orElseThrow(() -> new StockOperationException("No se encontró la carta en el inventario."));
    }

    private InventoryCard findCardForSale(CashRegisterEntry sale) throws Exception {
        List<InventoryCard> cards = store.getInventoryCards();

        if (sale.getCardId() != null && !sale.getCardId().isBlank()) {
            for (InventoryCard card : cards) {
                if (sale.getCardId().equals(card.getId())) {
                    return card;
                }
            }
        }

        for (InventoryCard card : cards) {
            if (same(card.getName(), sale.getName())
                    && same(card.getSetCode(), sale.getSetCode())
                    && same(card.getCollectorNumber(), sale.getCollectorNumber())
                    && same(card.getPrinting(), sale.getPrinting())) {
                return card;
            }
        }

        return null;
    }

    private void rollbackStock(InventoryCard card, int previous) {
        try {
            setQuantity(card, previous);
            store.updateStockState(card.getRowIndex(), card);
        } catch (Exception rollbackError) {
            log.error("No se pudo registrar la venta ni devolver el stock de {} (fila {}). Revisar a mano.",
                    card.getName(), card.getRowIndex(), rollbackError);
        }
    }

    void appendMovementQuietly(InventoryMovement movement) {
        try {
            store.appendMovement(movement);
        } catch (Exception e) {
            // El historial de movimientos es informativo: no se deshace una venta ya cobrada por esto.
            log.warn("No se pudo registrar el movimiento de {}.", movement.getName(), e);
        }
    }

    CashRegisterEntry cashEntry(LocalDateTime now, InventoryCard card, String type, int units, double unitPrice) {
        CashRegisterEntry entry = new CashRegisterEntry();
        entry.setDate(now.format(DATE_FORMAT));
        entry.setTime(now.format(TIME_FORMAT));
        entry.setType(type);
        entry.setName(card.getName());
        entry.setSetName(card.getSetName());
        entry.setSetCode(card.getSetCode());
        entry.setCollectorNumber(card.getCollectorNumber());
        entry.setPrinting(card.getPrinting());
        entry.setLocalPrice(String.valueOf(unitPrice));
        entry.setQuantity(String.valueOf(units));
        entry.setTotal(String.valueOf(unitPrice * units));
        entry.setCardId(card.getId());
        return entry;
    }

    InventoryMovement movement(
            LocalDateTime now,
            int signedQuantity,
            InventoryCard card,
            int previous,
            int next,
            String source
    ) {
        return new InventoryMovement(
                now.format(DATE_TIME_FORMAT),
                now.format(DATE_FORMAT),
                now.format(TIME_FORMAT),
                signedQuantity < 0 ? "SALIDA" : "ENTRADA",
                String.valueOf(signedQuantity),
                nullToEmpty(card.getName()),
                nullToEmpty(card.getSetName()),
                nullToEmpty(card.getSetCode()),
                nullToEmpty(card.getCollectorNumber()),
                nullToEmpty(card.getPrinting()),
                String.valueOf(previous),
                String.valueOf(next),
                source
        );
    }

    static void setQuantity(InventoryCard card, int quantity) {
        card.setQuantity(String.valueOf(quantity));
        card.setAction(actionFor(quantity));
    }

    public static String actionFor(int quantity) {
        return quantity <= 0 ? ACTION_OUT_OF_STOCK : ACTION_IN_STOCK;
    }

    static int quantity(InventoryCard card) {
        return Math.max(Amounts.parseInt(card.getQuantity()), 0);
    }

    private static boolean same(String first, String second) {
        return nullToEmpty(first).trim().equalsIgnoreCase(nullToEmpty(second).trim());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static String newSaleId() {
        return "V" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    public record StockChange(
            String cardId,
            int rowIndex,
            int stockQuantity,
            String action,
            String saleId,
            String source
    ) {
    }

    public record VoidResult(String saleId, boolean stockRestored, double amount) {
    }

    public static class StockOperationException extends Exception {
        public StockOperationException(String message) {
            super(message);
        }
    }
}
