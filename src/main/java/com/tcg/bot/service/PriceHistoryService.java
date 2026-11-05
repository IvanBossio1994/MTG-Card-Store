package com.tcg.bot.service;

import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.model.PricePoint;
import com.tcg.bot.util.Amounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Historial de precios guardado en la hoja "Precios" del Sheet de cada tienda (sin base de datos propia).
 * En cada sincronizacion se anota una fila por carta solo si su precio cambio desde la ultima foto,
 * asi la hoja crece poco aunque se sincronice todos los dias.
 */
@Service
public class PriceHistoryService {

    private static final Logger log = LoggerFactory.getLogger(PriceHistoryService.class);
    private static final double EPSILON = 0.005;

    private final InventoryStore store;
    private final Clock clock;

    @Autowired
    public PriceHistoryService(InventoryStore store) {
        this(store, Clock.system(ZoneId.of("America/Buenos_Aires")));
    }

    PriceHistoryService(InventoryStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    /** Variacion de una carta: precio actual contra el de hace ~30 dias (o el primero que haya). */
    public record Trend(
            String cardId,
            String name,
            String setName,
            String setCode,
            String collectorNumber,
            String printing,
            Double currentLocal,
            Double currentCkUsd,
            Double changePercent,
            int points
    ) {
        public String formattedLocal() {
            return currentLocal == null ? "-" : com.tcg.bot.i18n.Money.format(currentLocal);
        }

        public String formattedCkUsd() {
            return currentCkUsd == null ? "-" : com.tcg.bot.i18n.Money.usd(currentCkUsd);
        }

        public String formattedChange() {
            return formatPercent(changePercent);
        }

        public String changeClass() {
            if (changePercent == null) {
                return "";
            }
            return changePercent > 0.5 ? "trend up" : changePercent < -0.5 ? "trend down" : "trend flat";
        }
    }

    /** Anota los precios que cambiaron. No corta la sincronizacion si falla. */
    public int recordQuietly(List<InventoryCard> cards, Map<String, ScryfallPriceService.Prices> extraPrices) {
        try {
            return record(cards, extraPrices);
        } catch (Exception e) {
            log.warn("No se pudo guardar el historial de precios: {}", e.getMessage());
            return 0;
        }
    }

    public int record(List<InventoryCard> cards, Map<String, ScryfallPriceService.Prices> extraPrices) throws Exception {
        Map<String, PricePoint> last = latestByCard(store.getPriceHistory());
        String today = LocalDate.now(clock).toString();
        List<PricePoint> changed = new ArrayList<>();

        for (InventoryCard card : cards) {
            if (card.getId() == null || card.getId().isBlank()) {
                continue;
            }

            Double ck = Amounts.parseOrNull(card.getCkPriceUsd());
            Double local = Amounts.parseOrNull(card.getLocalPrice());
            if (ck == null && local == null) {
                continue;
            }

            ScryfallPriceService.Prices extra = extraPrices == null ? null
                    : extraPrices.get(ScryfallPriceService.key(card.getSetCode(), card.getCollectorNumber()));
            PricePoint point = new PricePoint(
                    today,
                    card.getId(),
                    card.getName(),
                    card.getSetName(),
                    card.getSetCode(),
                    card.getCollectorNumber(),
                    card.getPrinting(),
                    ck,
                    extra == null ? null : extra.tcgplayer(card.isFoil()),
                    extra == null ? null : extra.cardmarket(card.isFoil()),
                    local
            );

            PricePoint previous = last.get(card.getId());
            if (previous == null || differs(previous.ckUsd(), ck) || differs(previous.localPrice(), local)) {
                changed.add(point);
            }
        }

        store.appendPriceHistory(changed);
        return changed.size();
    }

    public List<PricePoint> history(List<PricePoint> all, String cardId) {
        return all.stream()
                .filter(point -> point.cardId().equals(cardId))
                .sorted(Comparator.comparing(PricePoint::date))
                .toList();
    }

    public List<Trend> trends(List<InventoryCard> cards, List<PricePoint> all) {
        Map<String, List<PricePoint>> byCard = new HashMap<>();
        for (PricePoint point : all) {
            byCard.computeIfAbsent(point.cardId(), key -> new ArrayList<>()).add(point);
        }

        String monthAgo = LocalDate.now(clock).minusDays(30).toString();
        List<Trend> trends = new ArrayList<>();

        for (InventoryCard card : cards) {
            if (card.getId() == null || card.getId().isBlank() || card.getName() == null || card.getName().isBlank()) {
                continue;
            }

            List<PricePoint> points = byCard.getOrDefault(card.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(PricePoint::date))
                    .toList();
            Double current = Amounts.parseOrNull(card.getLocalPrice());
            Double reference = null;
            for (PricePoint point : points) {
                if (point.localPrice() == null) {
                    continue;
                }
                // El ultimo punto de hace 30 dias o mas; si no hay, el mas viejo.
                if (point.date().compareTo(monthAgo) <= 0 || reference == null) {
                    reference = point.localPrice();
                }
                if (point.date().compareTo(monthAgo) > 0) {
                    break;
                }
            }

            Double change = current != null && reference != null && reference > 0 && points.size() > 1
                    ? (current - reference) / reference * 100
                    : null;

            trends.add(new Trend(card.getId(), card.getName(), card.getSetName(), card.getSetCode(),
                    card.getCollectorNumber(), card.getPrinting(), current,
                    Amounts.parseOrNull(card.getCkPriceUsd()), change, points.size()));
        }

        trends.sort(Comparator.comparing(Trend::name, String.CASE_INSENSITIVE_ORDER));
        return trends;
    }

    static Map<String, PricePoint> latestByCard(List<PricePoint> points) {
        Map<String, PricePoint> latest = new HashMap<>();
        for (PricePoint point : points) {
            PricePoint current = latest.get(point.cardId());
            if (current == null || point.date().compareTo(current.date()) >= 0) {
                latest.put(point.cardId(), point);
            }
        }
        return latest;
    }

    private static boolean differs(Double first, Double second) {
        if (first == null || second == null) {
            return first != second;
        }
        return Math.abs(first - second) > EPSILON;
    }

    public static String formatPercent(Double value) {
        if (value == null) {
            return "";
        }
        return String.format(Locale.ROOT, "%+.0f%%", value);
    }
}
