package com.tcg.bot.util;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AmountsTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "1500        | 1500",
            "1.500       | 1500",
            "$ 1.500     | 1500",
            "12.500      | 12500",
            "1.234.567   | 1234567",
            "1,500       | 1500",
            "1.500,50    | 1500.5",
            "1,500.50    | 1500.5",
            "2.49        | 2.49",
            "2,49        | 2.49",
            "1500.0      | 1500",
            "1.5E3       | 1500",
            "-1500       | -1500",
            "-1.500      | -1500",
    })
    void parsesSheetAmounts(String raw, double expected) {
        assertThat(Amounts.parse(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {"''", "abc", "'   '"})
    void invalidOrBlankIsZero(String raw) {
        assertThat(Amounts.parse(raw)).isZero();
        assertThat(Amounts.parseOrNull(raw)).isNull();
    }
}
