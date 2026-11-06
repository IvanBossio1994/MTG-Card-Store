package com.tcg.bot.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsappLinkTest {

    @Test
    void turnsArgentinePhonesIntoWhatsappNumbers() {
        assertThat(com.tcg.bot.util.Whatsapp.number("11 5555-1234")).isEqualTo("5491155551234");
        assertThat(com.tcg.bot.util.Whatsapp.number("011 15 5555-1234")).isEqualTo("5491155551234");
        assertThat(com.tcg.bot.util.Whatsapp.number("+54 9 11 5555 1234")).isEqualTo("5491155551234");
        assertThat(com.tcg.bot.util.Whatsapp.number("+1 415 555 0100")).isEqualTo("14155550100");
        assertThat(com.tcg.bot.util.Whatsapp.number("@juan.magic")).isNull();
        assertThat(com.tcg.bot.util.Whatsapp.number("juan@mail.com")).isNull();
        assertThat(com.tcg.bot.util.Whatsapp.number("")).isNull();
    }

    @Test
    void outsideArgentinaNeedsTheCountryCode() {
        String before = com.tcg.bot.i18n.Money.currency();
        try {
            com.tcg.bot.i18n.Money.setCurrency("EUR");
            assertThat(com.tcg.bot.util.Whatsapp.number("612 345 678")).isNull();
            assertThat(com.tcg.bot.util.Whatsapp.number("+34 612 345 678")).isEqualTo("34612345678");
            assertThat(com.tcg.bot.util.Whatsapp.number("0034 612 345 678")).isEqualTo("34612345678");
            assertThat(com.tcg.bot.util.Whatsapp.link("612 345 678", "hola")).isEqualTo("https://wa.me/?text=hola");
        } finally {
            com.tcg.bot.i18n.Money.setCurrency(before);
        }
    }
}
