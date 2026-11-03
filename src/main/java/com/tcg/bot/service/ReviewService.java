package com.tcg.bot.service;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.util.Amounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Para revisar": lo que conviene mirar antes de abrir la tienda. Todo sale del Sheet.
 * <ul>
 *     <li>Cartas con stock cuyo precio cambió mucho en el último mes.</li>
 *     <li>Cartas que se vienen vendiendo y quedan pocas o ninguna.</li>
 *     <li>Cartas con stock sin precio de venta.</li>
 *     <li>Cartas sin edición o número (no consiguen precio).</li>
 * </ul>
 */
@Service
public class ReviewService {

    /** Cambio de precio (en %) a partir del cual una carta aparece para revisar. */
    public static final double BIG_CHANGE_PERCENT = 15;
    /** Días hacia atrás para ver qué se vendió. */
    public static final int SALES_DAYS = 30;
    /** Con esta cantidad o menos, una carta que se vende se marca como poco stock. */
    public static final int LOW_STOCK = 1;

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final InventoryStore store;
    private final PriceHistoryService priceHistoryService;
    private final StockOperationsService stockOperations;

    public ReviewService(InventoryStore store, PriceHistoryService priceHistoryService,
                         StockOperationsService stockOperations) {
        this.store = store;
        this.priceHistoryService = priceHistoryService;
        this.stockOperations = stockOperations;
    }

    public record LowStock(InventoryCard card, int stock, int soldLastMonth) {
    }

    public record Review(
            List<PriceHistoryService.Trend> priceMoves,
            List<LowStock> lowStock,
            List<InventoryCard> withoutPrice,
            List<InventoryCard> withoutEdition
    ) {
        public int total() {
            return priceMoves.size() + lowStock.size() + withoutPrice.size() + withoutEdition.size();
        }
    }

    public Review review() throws Exception {
        List<InventoryCard> cards = store.getInventoryCards().stream()
                .filter(card -> card.getName() != null && !card.getName().isBlank())
                .toList();

        List<PriceHistoryService.Trend> moves = new ArrayList<>();
        try {
            for (var trend : priceHistoryService.trends(cards, store.getPriceHistory())) {
                if (trend.changePercent() != null && Math.abs(trend.changePercent()) >= BIG_CHANGE_PERCENT
                        && stockOf(cards, trend.cardId()) > 0) {
                    moves.add(trend);
                }
            }
            moves.sort(Comparator.comparing((PriceHistoryService.Trend trend) -> Math.abs(trend.changePercent())).reversed());
        } catch (Exception e) {
            log.debug("Para revisar: sin historial de precios: {}", e.getMessage());
        }

        Map<String, Integer> sold = new HashMap<>();
        try {
            String since = LocalDate.now(stockOperations.clock).minusDays(SALES_DAYS).toString();
            for (CashRegisterEntry entry : store.getCashRegisterEntries()) {
                if (entry.affectsCash() && entry.getCardId() != null && !entry.getCardId().isBlank()
                        && entry.getDate() != null && entry.getDate().compareTo(since) >= 0) {
                    sold.merge(entry.getCardId(), entry.signedQuantity(), Integer::sum);
                }
            }
        } catch (Exception e) {
            log.debug("Para revisar: no se pudo leer la caja: {}", e.getMessage());
        }

        List<LowStock> low = new ArrayList<>();
        List<InventoryCard> withoutPrice = new ArrayList<>();
        List<InventoryCard> withoutEdition = new ArrayList<>();
        for (InventoryCard card : cards) {
            int stock = StockOperationsService.quantity(card);
            int soldUnits = card.getId() == null ? 0 : sold.getOrDefault(card.getId(), 0);
            if (soldUnits > 0 && stock <= LOW_STOCK) {
                low.add(new LowStock(card, stock, soldUnits));
            }
            if (stock > 0 && Amounts.parse(card.getLocalPrice()) <= 0) {
                withoutPrice.add(card);
            }
            if (CardIdentifyService.needsIdentification(card)) {
                withoutEdition.add(card);
            }
        }
        low.sort(Comparator.comparingInt(LowStock::soldLastMonth).reversed());

        return new Review(moves, low, withoutPrice, withoutEdition);
    }

    private static int stockOf(List<InventoryCard> cards, String cardId) {
        return cards.stream()
                .filter(card -> cardId != null && cardId.equals(card.getId()))
                .mapToInt(StockOperationsService::quantity)
                .sum();
    }
}
