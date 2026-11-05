package com.tcg.bot.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @AfterEach
    void reset() {
        Money.setCurrency("ARS");
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void pesosStayWholeNumbers() {
        Money.setCurrency("ARS");
        LocaleContextHolder.setLocale(Locale.forLanguageTag("es"));
        assertThat(Money.decimals()).isZero();
        assertThat(Money.round(1499.6)).isEqualTo(1500);
        assertThat(Money.format(1500)).isEqualTo("$ 1.500");
        assertThat(Money.plain(1500)).isEqualTo("1500");
        assertThat(Money.sheetValue(1500.4)).isEqualTo(1500L);
    }

    @Test
    void dollarsKeepCents() {
        Money.setCurrency("USD");
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(Money.decimals()).isEqualTo(2);
        assertThat(Money.round(4.499)).isEqualTo(4.5);
        assertThat(Money.format(1234.5)).isEqualTo("US$ 1,234.50");
        assertThat(Money.plain(4.5)).isEqualTo("4.50");
        assertThat(Money.sheetValue(4.5)).isEqualTo(4.5);
        assertThat(Money.isZero(0.004)).isTrue();
        assertThat(Money.isZero(0.01)).isFalse();
    }

    @Test
    void euroCentsUseTheScreenLanguageSeparators() {
        Money.setCurrency("EUR");
        LocaleContextHolder.setLocale(Locale.GERMAN);
        assertThat(Money.number(1234.5)).isEqualTo("1.234,50");
    }

    @Test
    void formFieldsHaveNoTrailingZeros() {
        assertThat(Money.plainNumber(500.0)).isEqualTo("500");
        assertThat(Money.plainNumber(0.25)).isEqualTo("0.25");
    }
}
