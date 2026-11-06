package com.tcg.bot.service;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.WantedCard;
import com.tcg.bot.service.ImportListParser.ParsedImportLine;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cartas buscadas: lo que un cliente pide y no hay. Cuando una de esas cartas entra al stock
 * (por una compra, una importación o a mano) la app la marca como "ya entró" para avisarle.
 */
@Service
public class WantedService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    static final int MAX_LINES = 100;

    private final InventoryStore store;
    private final StockOperationsService stockOperations;

    public WantedService(InventoryStore store, StockOperationsService stockOperations) {
        this.store = store;
        this.stockOperations = stockOperations;
    }

    /** Una carta buscada y cuántas unidades hay ahora en stock que le sirven. */
    public record View(WantedCard wanted, int available, List<InventoryCard> matches) {
        public boolean arrived() {
            return available > 0;
        }
    }

    /** Las buscadas abiertas: primero las que ya entraron y no se avisaron, después el resto, más nuevas arriba. */
    public List<View> open() throws Exception {
        List<WantedCard> wanted = store.getWantedCards().stream().filter(WantedCard::isOpen).toList();
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<InventoryCard> stock = inStock(store.getAllStockCards());
        return wanted.stream()
                .map(card -> view(card, stock))
                .sorted(Comparator.comparing((View view) -> !(view.arrived() && !view.wanted().isNotified()))
                        .thenComparing(view -> view.wanted().getDate(), Comparator.reverseOrder()))
                .toList();
    }

    /** Cuántas buscadas sin avisar ya tienen stock (para el resumen de Inicio). */
    public static int arrivedCount(List<WantedCard> wanted, List<InventoryCard> allStock) {
        List<InventoryCard> stock = inStock(allStock);
        return (int) wanted.stream()
                .filter(card -> card.isOpen() && !card.isNotified())
                .filter(card -> view(card, stock).arrived())
                .count();
    }

    /** Quién busca alguna de estas cartas (sin avisar todavía): para avisar justo cuando entran. */
    public List<WantedCard> waitingFor(List<InventoryCard> arrivedCards) throws Exception {
        List<InventoryCard> stock = inStock(arrivedCards);
        if (stock.isEmpty()) {
            return List.of();
        }
        return store.getWantedCards().stream()
                .filter(card -> card.isOpen() && !card.isNotified())
                .filter(card -> view(card, stock).arrived())
                .toList();
    }

    /** Anota una lista (una carta por renglón, con cantidad y edición opcionales) para un cliente. */
    public List<WantedCard> add(String customer, String contact, String list) throws Exception {
        String who = customer == null ? "" : customer.trim();
        if (who.isEmpty()) {
            throw new StockOperationException(I18n.t("wanted.err.customer"));
        }
        List<ParsedImportLine> lines = ImportListParser.parseAndMergeImportLines(list == null ? "" : list).stream()
                .filter(line -> line.quantity() > 0 && !DeckCheckService.isSectionTitle(line.name())
                        && !DeckCheckService.isSectionTitle(line.originalLine()))
                .toList();
        if (lines.isEmpty()) {
            throw new StockOperationException(I18n.t("wanted.err.empty"));
        }
        if (lines.size() > MAX_LINES) {
            throw new StockOperationException(I18n.t("wanted.err.tooMany", MAX_LINES));
        }

        LocalDateTime now = LocalDateTime.now(stockOperations.clock);
        List<WantedCard> cards = new ArrayList<>();
        for (ParsedImportLine line : lines) {
            WantedCard card = new WantedCard();
            card.setDate(now.toLocalDate().toString());
            card.setCustomer(who);
            card.setContact(contact == null ? "" : contact.trim());
            card.setName(line.name().trim());
            card.setSetCode(line.setCode() == null ? "" : line.setCode().trim().toUpperCase());
            card.setQuantity(line.quantity());
            card.setStatus(WantedCard.STATUS_WANTED);
            card.setUpdatedAt(now.format(TIME_FORMAT));
            card.setId("B" + StockOperationsService.newSaleId());
            cards.add(card);
        }
        synchronized (StockOperationsService.writeLock()) {
            store.appendWantedCards(cards);
        }
        return cards;
    }

    public WantedCard markNotified(String id) throws Exception {
        return setStatus(id, WantedCard.STATUS_NOTIFIED);
    }

    public WantedCard close(String id) throws Exception {
        return setStatus(id, WantedCard.STATUS_CLOSED);
    }

    private WantedCard setStatus(String id, String status) throws Exception {
        synchronized (StockOperationsService.writeLock()) {
            WantedCard card = store.getWantedCards().stream()
                    .filter(candidate -> id != null && id.equals(candidate.getId()))
                    .findFirst()
                    .orElseThrow(() -> new StockOperationException(I18n.t("wanted.err.notFound")));
            card.setStatus(status);
            card.setUpdatedAt(LocalDateTime.now(stockOperations.clock).format(TIME_FORMAT));
            store.updateWantedCard(card);
            return card;
        }
    }

    /** Fecha de hoy para el formulario, según el reloj de la app. */
    public LocalDate today() {
        return LocalDate.now(stockOperations.clock);
    }

    private static List<InventoryCard> inStock(List<InventoryCard> cards) {
        return cards.stream()
                .filter(card -> card.getName() != null && !card.getName().isBlank())
                .filter(card -> StockOperationsService.quantity(card) > 0)
                .toList();
    }

    /** Coincide por nombre (cualquier cara) y, si la buscada pide una edición, solo esa. */
    private static View view(WantedCard wanted, List<InventoryCard> stock) {
        Set<String> names = new HashSet<>(ImportListParser.importNameCandidates(wanted.getName()));
        String set = wanted.getSetCode() == null ? "" : wanted.getSetCode().trim();
        List<InventoryCard> matches = stock.stream()
                .filter(card -> ImportListParser.importNameCandidates(card.getName()).stream().anyMatch(names::contains))
                .filter(card -> set.isEmpty() || set.equalsIgnoreCase(card.getSetCode()))
                .toList();
        int available = matches.stream().mapToInt(StockOperationsService::quantity).sum();
        return new View(wanted, available, matches);
    }
}
