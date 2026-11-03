package com.tcg.bot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageCacheServiceTest {

    @Test
    void onlyCardImageSitesOverHttpsAreAllowed() {
        assertThat(ImageCacheService.allowed("https://api.scryfall.com/cards/named?exact=Sol%20Ring&format=image")).isTrue();
        assertThat(ImageCacheService.allowed("https://images.ygoprodeck.com/images/cards_small/46986414.jpg")).isTrue();
        assertThat(ImageCacheService.allowed("https://assets.tcgdex.net/en/sv/sv03/63/low.webp")).isTrue();
        assertThat(ImageCacheService.allowed("http://images.ygoprodeck.com/x.jpg")).isFalse();
        assertThat(ImageCacheService.allowed("https://example.com/x.jpg")).isFalse();
        assertThat(ImageCacheService.allowed("https://127.0.0.1/x.jpg")).isFalse();
        assertThat(ImageCacheService.allowed("not a url")).isFalse();
    }
}
