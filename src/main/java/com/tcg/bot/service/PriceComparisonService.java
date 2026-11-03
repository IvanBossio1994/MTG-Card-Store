package com.tcg.bot.service;

import java.util.List;

import com.tcg.bot.dto.CardKingdomProduct;
import com.tcg.bot.model.InventoryCard;
import org.springframework.stereotype.Service;

@Service
public class PriceComparisonService {

    private final PricingSettingsService pricingSettingsService;

    public PriceComparisonService(PricingSettingsService pricingSettingsService) {
        this.pricingSettingsService = pricingSettingsService;
    }

    /**
     * Precio en ARS = USD x cotizacion x (1 + recargo%), redondeado hacia arriba al multiplo,
     * y nunca por debajo del precio minimo.
     */
    public double calculateLocalPrice(double ckUsdPrice) {
        return finalizeLocalPrice(ckUsdPrice * pricingSettingsService.getCkDollarRate());
    }

    /** Aplica recargo, redondeo y minimo a un precio base ya convertido a ARS. */
    public double finalizeLocalPrice(double baseArs) {
        double rawPrice = baseArs * (1 + pricingSettingsService.getMarkupPercent() / 100.0);
        double rounded = roundUpToMultiple(rawPrice, pricingSettingsService.getRoundMultiple());
        return Math.max(rounded, pricingSettingsService.getMinimumPrice());
    }

    /** Precio de una fuente, en su moneda y convertido a ARS (sin recargo ni redondeo). */
    public record SourcePrice(String source, String currency, double price, double ars) {
    }

    public record Quote(List<SourcePrice> sources, Double localPrice) {
        public SourcePrice find(String source) {
            return sources.stream().filter(price -> price.source().equals(source)).findFirst().orElse(null);
        }
    }

    public static final String CARD_KINGDOM = "Card Kingdom";
    public static final String TCGPLAYER = "TCGplayer";
    public static final String CARDMARKET = "Cardmarket";

    /**
     * Junta las fuentes activas y sugiere el precio local segun el modo elegido.
     * Card Kingdom siempre se muestra; las demas solo si estan activas y tienen precio.
     */
    public Quote quote(Double ckUsd, ScryfallPriceService.Prices scryfall, boolean foil) {
        List<SourcePrice> sources = new java.util.ArrayList<>();
        double usdRate = pricingSettingsService.getCkDollarRate();

        if (ckUsd != null) {
            sources.add(new SourcePrice(CARD_KINGDOM, "USD", ckUsd, ckUsd * usdRate));
        }

        if (scryfall != null && pricingSettingsService.isTcgplayerEnabled()) {
            Double usd = scryfall.tcgplayer(foil);
            if (usd != null) {
                sources.add(new SourcePrice(TCGPLAYER, "USD", usd, usd * usdRate));
            }
        }

        if (scryfall != null && pricingSettingsService.isCardmarketEnabled() && pricingSettingsService.getEurRate() > 0) {
            Double eur = scryfall.cardmarket(foil);
            if (eur != null) {
                sources.add(new SourcePrice(CARDMARKET, "EUR", eur, eur * pricingSettingsService.getEurRate()));
            }
        }

        if (sources.isEmpty()) {
            return new Quote(sources, null);
        }

        var mode = pricingSettingsService.getCombineMode();
        var arsValues = sources.stream().mapToDouble(SourcePrice::ars);
        Double base = switch (mode) {
            case CARD_KINGDOM -> ckUsd == null ? null : ckUsd * usdRate;
            case AVERAGE -> arsValues.average().orElse(0);
            case MAX -> arsValues.max().orElse(0);
            case MIN -> arsValues.min().orElse(0);
        };

        return new Quote(sources, base == null ? null : finalizeLocalPrice(base));
    }

    private double roundUpToMultiple(double value, int multiple) {
        return Math.ceil(value / multiple) * multiple;
    }

    public Double extractConditionPrice(CardKingdomProduct product, String condition) {
        if (product == null || product.getConditionValues() == null) {
            return null;
        }

        var values = product.getConditionValues();
        String price = switch (normalizeCondition(condition)) {
            case "NM" -> values.getNmPrice();
            case "LP", "EX" -> values.getExPrice();
            case "VG" -> values.getVgPrice();
            case "G" -> values.getGPrice();
            default -> null;
        };

        if (price == null || price.isBlank()) {
            return null;
        }

        return Double.parseDouble(price);
    }

    public Double getBestConditionPrice(CardKingdomProduct product, InventoryCard card) {
        if (product == null || product.getConditionValues() == null) {
            return null;
        }

        String condition = card.getCondition() != null && !card.getCondition().isBlank()
                ? card.getCondition()
                : "NM";

        return extractConditionPrice(product, condition);
    }

    private String normalizeCondition(String condition) {
        if (condition == null || condition.isBlank()) {
            return "NM";
        }

        String normalized = condition
                .toUpperCase()
                .replaceAll("[^A-Z0-9]+", "");

        return switch (normalized) {
            case "NM", "NEARMINT", "MINT", "NORMAL" -> "NM";
            case "LP", "EX", "EXCELLENT", "LIGHTLYPLAYED" -> "EX";
            case "VG", "VERYGOOD", "MP", "MODERATELYPLAYED" -> "VG";
            case "G", "GOOD", "HP", "HEAVILYPLAYED", "DAMAGED", "DMG" -> "G";
            default -> normalized;
        };
    }
}
