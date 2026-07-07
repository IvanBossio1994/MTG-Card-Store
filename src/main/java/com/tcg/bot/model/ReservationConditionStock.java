package com.tcg.bot.model;

public record ReservationConditionStock(
        String condition,
        int quantity,
        int availableQuantity,
        int reservedQuantity,
        String action,
        int rowIndex,
        String ckPriceUsd,
        String localPrice
) {
    public String displayStockBreakdown() {
        return quantity
                + " total | "
                + reservedQuantity
                + " reservadas"
                + " | "
                + availableQuantity
                + " disponibles";
    }
}
