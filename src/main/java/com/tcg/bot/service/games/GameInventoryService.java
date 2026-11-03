package com.tcg.bot.service.games;

import com.tcg.bot.model.CashRegisterEntry;
import com.tcg.bot.model.Game;
import com.tcg.bot.model.GameCard;
import com.tcg.bot.model.GamePrint;
import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.service.InventoryStore;
import com.tcg.bot.service.LicenseService;
import com.tcg.bot.service.PriceComparisonService;
import com.tcg.bot.service.StockOperationsService;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import com.tcg.bot.service.StockOperationsBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Inventario de Pokémon y Yu-Gi-Oh!: buscar en el catálogo, sumar/restar stock, vender (va a la misma Caja
 * que Magic) y actualizar precios. El precio local usa la misma cotización y redondeo que Magic.
 */
@Service
public class GameInventoryService {

    private static final Logger log = LoggerFactory.getLogger(GameInventoryService.class);
    static final int MAX_NAMES_PER_REFRESH = 150;

    private final InventoryStore store;
    private final StockOperationsBridge stock;
    private final PriceComparisonService priceComparisonService;
    private final LicenseService licenseService;
    private final List<GameCatalog> catalogs;

    public GameInventoryService(InventoryStore store, StockOperationsBridge stock,
                                PriceComparisonService priceComparisonService, LicenseService licenseService,
                                List<GameCatalog> catalogs) {
        this.store = store;
        this.stock = stock;
        this.priceComparisonService = priceComparisonService;
        this.licenseService = licenseService;
        this.catalogs = List.copyOf(catalogs);
    }

    public record Result(GamePrint print, GameCard stockCard, double suggestedLocal) {
        public int stock() {
            return stockCard == null ? 0 : stockCard.getQuantity();
        }

        public String formattedSuggested() {
            return suggestedLocal > 0 ? "$ " + new java.text.DecimalFormat("#,##0",
                    java.text.DecimalFormatSymbols.getInstance(new java.util.Locale("es", "AR"))).format(Math.round(suggestedLocal)) : "-";
        }

        public String formattedUsd() {
            return print.usd() == null ? "-" : "US$ " + String.format(java.util.Locale.US, "%.2f", print.usd()).replace('.', ',');
        }
    }

    public List<Result> search(Game game, String name) throws Exception {
        List<GamePrint> prints = catalog(game).search(name);
        Map<String, GameCard> byCatalog = new HashMap<>();
        for (GameCard card : store.getGameCards(game)) {
            byCatalog.put(card.getCatalogId(), card);
        }
        List<Result> results = new ArrayList<>();
        for (GamePrint print : prints) {
            results.add(new Result(print, byCatalog.get(print.catalogId()), localPrice(print.usd())));
        }
        // Primero lo que ya tenés en stock.
        results.sort(Comparator.comparing((Result result) -> result.stock() > 0).reversed());
        return results;
    }

    public List<GameCard> cards(Game game) throws Exception {
        List<GameCard> cards = new ArrayList<>(store.getGameCards(game));
        cards.sort(Comparator.comparing((GameCard card) -> card.getName() == null ? "" : card.getName(), String.CASE_INSENSITIVE_ORDER));
        return cards;
    }

    /** Suma una unidad de esa impresión; si no estaba en el Sheet, crea la fila. */
    public GameCard add(Game game, GamePrint print) throws Exception {
        synchronized (StockOperationsService.writeLock()) {
            List<GameCard> cards = store.getGameCards(game);
            GameCard existing = cards.stream()
                    .filter(card -> print.catalogId().equals(card.getCatalogId()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                return change(game, existing.getId(), 1, false);
            }

            if (licenseService.remainingNewCards(distinctCardsInStore()) <= 0) {
                throw new StockOperationException(licenseService.limitMessage());
            }

            GameCard card = new GameCard();
            card.setQuantity(1);
            card.setName(print.name());
            card.setSetName(print.setName());
            card.setSetCode(print.setCode());
            card.setNumber(print.number());
            card.setRarity(print.rarity());
            card.setFinish(print.finish());
            card.setUsd(print.usd());
            card.setLocalPrice(localPrice(print.usd()));
            card.setId("G" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            card.setCatalogId(print.catalogId());
            card.setRowIndex(store.appendGameCard(game, card));
            stock.movement(card.asInventoryCard(game), 1, 0, 1, StockOperationsService.SOURCE_ADDED);
            return card;
        }
    }

    /** +1 / -1. Restar como venta registra la venta en la Caja de la tienda. */
    public GameCard change(Game game, String id, int delta, boolean sale) throws Exception {
        synchronized (StockOperationsService.writeLock()) {
            GameCard card = store.getGameCards(game).stream()
                    .filter(candidate -> id != null && id.equals(candidate.getId()))
                    .findFirst()
                    .orElseThrow(() -> new StockOperationException("La carta ya no está en la pestaña " + game.sheetName() + "."));
            int previous = card.getQuantity();
            int next = Math.max(previous + delta, 0);
            if (next == previous) {
                throw new StockOperationException("No hay unidades de " + card.getName() + " para descontar.");
            }
            boolean isSale = sale && delta < 0;
            if (isSale && card.getLocalPrice() <= 0) {
                throw new StockOperationException(card.getName() + " no tiene precio. Actualizá precios o usá \"Ajuste\".");
            }

            card.setQuantity(next);
            store.updateGameCard(game, card);
            if (isSale) {
                try {
                    stock.sale(card.asInventoryCard(game), previous - next, card.getLocalPrice());
                } catch (Exception e) {
                    card.setQuantity(previous);
                    store.updateGameCard(game, card);
                    throw e;
                }
            }
            stock.movement(card.asInventoryCard(game), next - previous, previous, next,
                    delta > 0 ? StockOperationsService.SOURCE_ADDED
                            : isSale ? StockOperationsService.SOURCE_SOLD : StockOperationsService.SOURCE_ADJUSTED);
            return card;
        }
    }

    /** Vuelve a pedir precios al catálogo y recalcula el precio local. Devuelve cuántas filas cambiaron. */
    public int refreshPrices(Game game) throws Exception {
        List<GameCard> cards = store.getGameCards(game);
        Set<String> names = new LinkedHashSet<>();
        cards.forEach(card -> names.add(card.getName()));

        Map<String, Double> prices = new HashMap<>();
        int asked = 0;
        for (String name : names) {
            if (asked++ >= MAX_NAMES_PER_REFRESH) {
                break;
            }
            try {
                for (GamePrint print : catalog(game).search(name)) {
                    prices.put(print.catalogId(), print.usd());
                }
                Thread.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.debug("No se pudo actualizar {}: {}", name, e.getMessage());
            }
        }

        int changed = 0;
        synchronized (StockOperationsService.writeLock()) {
            for (GameCard card : store.getGameCards(game)) {
                if (!prices.containsKey(card.getCatalogId())) {
                    continue;
                }
                Double usd = prices.get(card.getCatalogId());
                double local = localPrice(usd);
                if (!java.util.Objects.equals(usd, card.getUsd()) || Math.round(local) != Math.round(card.getLocalPrice())) {
                    card.setUsd(usd);
                    if (local > 0) {
                        card.setLocalPrice(local);
                    }
                    store.updateGameCard(game, card);
                    changed++;
                }
            }
        }
        return changed;
    }

    private double localPrice(Double usd) {
        return usd == null || usd <= 0 ? 0 : priceComparisonService.calculateLocalPrice(usd);
    }

    private int distinctCardsInStore() throws Exception {
        int total = (int) store.getInventoryCards().stream()
                .filter(card -> card.getName() != null && !card.getName().isBlank())
                .count();
        for (Game other : Game.values()) {
            total += store.getGameCards(other).size();
        }
        return total;
    }

    private GameCatalog catalog(Game game) {
        return catalogs.stream()
                .filter(catalog -> catalog.game() == game)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Sin catálogo para " + game.label()));
    }
}
