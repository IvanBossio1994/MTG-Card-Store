package com.tcg.bot.service;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.i18n.Money;
import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.Consignment;
import com.tcg.bot.model.GameCard;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Consignación: un cliente deja cartas, la tienda las vende y le paga su parte cuando se venden.
 * <p>
 * Cada entrega se guarda como una fila propia del inventario (con su ID y "Consignación: dueño" como ubicación),
 * así se vende igual que cualquier carta y sus ventas en la caja son del dueño, sin mezclarse con las copias de la
 * tienda. El costo de esa fila es la parte del dueño, para que la ganancia de los reportes sea la de la tienda.
 * Lo que se le debe sale de la caja (ventas menos anulaciones de esa fila) por el % acordado, menos lo ya pagado.
 */
@Service
public class ConsignmentService {

    public record Line(InventoryCard card, int quantity) {
    }

    public enum Payout { CASH, CREDIT }

    public record Item(Consignment consignment, int sold, int inStock, double owed) {
    }

    public record Owner(String name, String contact, List<Item> items, int inStock, int sold, double owed) {
        public String formattedOwed() {
            return Money.format(owed);
        }
    }

    private final InventoryStore store;
    private final StockOperationsService stockOperations;
    private final CreditService creditService;

    public ConsignmentService(InventoryStore store, StockOperationsService stockOperations, CreditService creditService) {
        this.store = store;
        this.stockOperations = stockOperations;
        this.creditService = creditService;
    }

    /** Recibe cartas en consignación: una fila nueva de inventario por carta y una fila en la pestaña Consignacion. */
    public List<Consignment> receive(String owner, String contact, double percent, List<Line> lines) throws Exception {
        if (owner == null || owner.isBlank()) {
            throw new StockOperationException(I18n.t("consign.err.owner"));
        }
        if (lines == null || lines.isEmpty()) {
            throw new StockOperationException(I18n.t("buy.err.empty"));
        }
        if (percent <= 0 || percent > 100) {
            throw new StockOperationException(I18n.t("consign.err.percent"));
        }
        for (Line line : lines) {
            if (line.quantity() <= 0) {
                throw new StockOperationException(I18n.t("buy.err.line", line.card().getName()));
            }
        }

        String who = owner.trim();
        String where = I18n.t("consign.location", who);
        synchronized (StockOperationsService.writeLock()) {
            LocalDateTime now = LocalDateTime.now(stockOperations.clock);
            List<Consignment> items = new ArrayList<>();
            for (Line line : lines) {
                InventoryCard original = line.card();
                double price = original.localPriceValue();
                Double ownerShare = price > 0 ? Money.round(price * percent / 100.0) : null;
                InventoryCard copy = original.getGame() == null
                        ? appendMagicCopy(original, line.quantity(), ownerShare, where)
                        : appendGameCopy(original, line.quantity(), ownerShare, where);
                stockOperations.appendMovementQuietly(stockOperations.movement(now, line.quantity(), copy, 0,
                        line.quantity(), I18n.t("consign.movement", who)));

                Consignment item = new Consignment();
                item.setDate(now.toLocalDate().toString());
                item.setOwner(who);
                item.setContact(contact == null ? "" : contact.trim());
                item.setCardId(copy.getId());
                item.setName(copy.getName());
                item.setEdition(original.getSetName() == null || original.getSetName().isBlank()
                        ? original.getSetCode() : original.getSetName());
                item.setQuantity(line.quantity());
                item.setPercent(percent);
                item.setPaid(0);
                item.setStatus(Consignment.STATUS_ACTIVE);
                item.setId("K" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT));
                items.add(item);
            }
            store.appendConsignments(items);
            return items;
        }
    }

    private InventoryCard appendMagicCopy(InventoryCard original, int quantity, Double cost, String where) throws Exception {
        InventoryCard copy = new InventoryCard();
        copy.setId(newCardId());
        copy.setName(original.getName());
        copy.setSetCode(original.getSetCode());
        copy.setSetName(original.getSetName());
        copy.setCollectorNumber(original.getCollectorNumber());
        copy.setCondition(original.getCondition());
        copy.setPrinting(original.getPrinting());
        copy.setLanguage(original.getLanguage());
        copy.setLocalPrice(original.getLocalPrice());
        copy.setCkPriceUsd(original.getCkPriceUsd());
        copy.setCost(cost == null ? "" : Money.plain(cost));
        copy.setLocation(where);
        StockOperationsService.setQuantity(copy, quantity);
        copy.setRowIndex(store.appendInventoryCard(copy));
        return copy;
    }

    private InventoryCard appendGameCopy(InventoryCard original, int quantity, Double cost, String where) throws Exception {
        GameCard source = store.getGameCards(original.getGame()).stream()
                .filter(card -> card.getId() != null && card.getId().equals(original.getId()))
                .findFirst()
                .orElseThrow(() -> new StockOperationException(I18n.t("buy.err.cardNotFound")));
        GameCard copy = GameCard.fromRow(source.toRow().stream().map(String::valueOf).toList(), 0);
        copy.setId("G" + newCardId());
        copy.setQuantity(quantity);
        copy.setCost(cost);
        copy.setLocation(where);
        copy.setRowIndex(store.appendGameCard(original.getGame(), copy));
        return copy.asInventoryCard(original.getGame());
    }

    private static String newCardId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    /** Dueños con cartas a la venta o plata por cobrar, con lo vendido y lo que se les debe. */
    public List<Owner> owners() throws Exception {
        List<Consignment> items = store.getConsignments();
        if (items.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> stock = new HashMap<>();
        for (InventoryCard card : store.getAllStockCards()) {
            if (card.getId() != null) {
                stock.merge(card.getId(), StockOperationsService.quantity(card), Integer::sum);
            }
        }
        Map<String, int[]> soldUnits = new HashMap<>();
        Map<String, Double> soldTotal = new HashMap<>();
        for (CashRegisterEntry entry : store.getCashRegisterEntries()) {
            if (entry.affectsCash() && entry.getCardId() != null && !entry.getCardId().isBlank()) {
                soldUnits.computeIfAbsent(entry.getCardId(), key -> new int[1])[0] += entry.signedQuantity();
                soldTotal.merge(entry.getCardId(), entry.signedTotal(), Double::sum);
            }
        }

        Map<String, List<Item>> byOwner = new LinkedHashMap<>();
        Map<String, String> contacts = new HashMap<>();
        for (Consignment item : items) {
            int sold = Math.max(0, soldUnits.getOrDefault(item.getCardId(), new int[1])[0]);
            int inStock = item.isReturned() ? 0 : stock.getOrDefault(item.getCardId(), 0);
            double owed = owed(item, soldTotal.getOrDefault(item.getCardId(), 0.0));
            if (inStock <= 0 && Money.isZero(owed)) {
                continue;
            }
            String key = item.getOwner().trim().toLowerCase(Locale.ROOT);
            byOwner.computeIfAbsent(key, k -> new ArrayList<>()).add(new Item(item, sold, inStock, owed));
            if (item.getContact() != null && !item.getContact().isBlank()) {
                contacts.put(key, item.getContact());
            }
        }

        List<Owner> owners = new ArrayList<>();
        for (var entry : byOwner.entrySet()) {
            List<Item> list = entry.getValue();
            owners.add(new Owner(list.get(0).consignment().getOwner(), contacts.getOrDefault(entry.getKey(), ""), list,
                    list.stream().mapToInt(Item::inStock).sum(),
                    list.stream().mapToInt(Item::sold).sum(),
                    Money.round(list.stream().mapToDouble(Item::owed).sum())));
        }
        owners.sort((a, b) -> Double.compare(b.owed(), a.owed()));
        return owners;
    }

    /** La parte del dueño de lo cobrado por esa fila, menos lo ya pagado. Nunca negativo. */
    static double owed(Consignment item, double soldTotal) {
        return Math.max(0, Money.round(soldTotal * item.getPercent() / 100.0) - item.getPaid());
    }

    /** Le paga al dueño todo lo que se le debe, en efectivo (sale de la caja) o como crédito de la tienda. */
    public double pay(String owner, Payout payout) throws Exception {
        synchronized (StockOperationsService.writeLock()) {
            Owner found = owners().stream()
                    .filter(candidate -> candidate.name().equalsIgnoreCase(owner == null ? "" : owner.trim()))
                    .findFirst()
                    .orElseThrow(() -> new StockOperationException(I18n.t("consign.err.notFound")));
            if (Money.isZero(found.owed())) {
                throw new StockOperationException(I18n.t("consign.err.nothingOwed", found.name()));
            }
            String reference = "K" + StockOperationsService.newSaleId();
            if (payout == Payout.CREDIT) {
                creditService.add(found.name(), found.contact(), found.owed(), I18n.t("consign.creditReason"), reference);
            } else {
                LocalDateTime now = LocalDateTime.now(stockOperations.clock);
                CashRegisterEntry entry = new CashRegisterEntry();
                entry.setDate(now.toLocalDate().toString());
                entry.setTime(now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                entry.setType(CashRegisterEntry.TYPE_BUY);
                entry.setName(I18n.t("consign.cashEntry", found.name()));
                entry.setQuantity("0");
                entry.setTotal(Money.plain(found.owed()));
                entry.setSaleId(reference);
                store.appendCashEntry(entry);
            }
            for (Item item : found.items()) {
                if (item.owed() > 0) {
                    Consignment consignment = item.consignment();
                    consignment.setPaid(Money.round(consignment.getPaid() + item.owed()));
                    store.updateConsignment(consignment);
                }
            }
            return found.owed();
        }
    }

    /** Devuelve al dueño lo que quedó sin vender de una entrega: saca ese stock y la marca devuelta. */
    public int giveBack(String id) throws Exception {
        synchronized (StockOperationsService.writeLock()) {
            Consignment item = store.getConsignments().stream()
                    .filter(candidate -> candidate.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new StockOperationException(I18n.t("consign.err.notFound")));
            if (item.isReturned()) {
                return 0;
            }
            InventoryCard card = store.getAllStockCards().stream()
                    .filter(candidate -> item.getCardId().equals(candidate.getId()))
                    .findFirst()
                    .orElse(null);
            int units = card == null ? 0 : StockOperationsService.quantity(card);
            if (units > 0) {
                stockOperations.changeStock(card.getId(), card.getRowIndex(), -units, StockOperationsService.Reason.ADJUST);
            }
            item.setStatus(Consignment.STATUS_RETURNED);
            store.updateConsignment(item);
            return units;
        }
    }
}
