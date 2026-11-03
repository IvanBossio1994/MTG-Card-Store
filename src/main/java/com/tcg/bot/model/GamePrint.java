package com.tcg.bot.model;

/** Una impresión de una carta de otro juego, tal como la devuelve el catálogo (pokemontcg.io / YGOPRODeck). */
public record GamePrint(
        String catalogId,
        String name,
        String setName,
        String setCode,
        String number,
        String rarity,
        String finish,
        String imageUrl,
        Double usd,
        String releasedAt
) {
}
