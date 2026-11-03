package com.tcg.bot.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsappLinkTest {

    @Test
    void turnsArgentinePhonesIntoWhatsappNumbers() {
        assertThat(ReservationController.whatsappNumber("11 5555-1234")).isEqualTo("5491155551234");
        assertThat(ReservationController.whatsappNumber("011 15 5555-1234")).isEqualTo("5491155551234");
        assertThat(ReservationController.whatsappNumber("+54 9 11 5555 1234")).isEqualTo("5491155551234");
        assertThat(ReservationController.whatsappNumber("+1 415 555 0100")).isEqualTo("14155550100");
        assertThat(ReservationController.whatsappNumber("@juan.magic")).isNull();
        assertThat(ReservationController.whatsappNumber("juan@mail.com")).isNull();
        assertThat(ReservationController.whatsappNumber("")).isNull();
    }
}
