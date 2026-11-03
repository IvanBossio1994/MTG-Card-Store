package com.tcg.bot.model;

/**
 * Una foto del precio de una carta en un dia. Se guarda en la hoja "Precios" del Sheet
 * solo cuando el precio cambio respecto de la ultima foto.
 */
public record PricePoint(
        String date,
        String cardId,
        String name,
        String setName,
        String setCode,
        String collectorNumber,
        String printing,
        Double ckUsd,
        Double tcgplayerUsd,
        Double cardmarketEur,
        Double localPrice
) {
}
