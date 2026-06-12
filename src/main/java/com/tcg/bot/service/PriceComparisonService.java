package com.tcg.bot.service;

import com.tcg.bot.dto.CardKingdomProduct;
import com.tcg.bot.model.InventoryCard;
import org.springframework.stereotype.Service;

@Service
public class PriceComparisonService {

    private final PricingSettingsService pricingSettingsService;

    public PriceComparisonService(PricingSettingsService pricingSettingsService) {
        this.pricingSettingsService = pricingSettingsService;
    }

    public double calculateLocalPrice(double ckUsdPrice) {
        double rawPrice = ckUsdPrice * pricingSettingsService.getCkDollarRate();
        return roundUpToMultiple(rawPrice, pricingSettingsService.getRoundMultiple());
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
