package com.tcg.bot.update;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppVersionTest {

    @Test
    void parsesTagsAndComparesNumbers() {
        assertThat(AppVersion.parse("v1.10.0").isNewerThan(AppVersion.parse("1.9.3"))).isTrue();
        assertThat(AppVersion.parse("1.0").compareTo(AppVersion.parse("1.0.0"))).isZero();
        assertThat(AppVersion.parse("1.0.1").isNewerThan(AppVersion.parse("1.0.1"))).isFalse();
        assertThat(AppVersion.parse("v2.0.0-beta").toString()).isEqualTo("2.0.0");
    }

    @Test
    void rejectsNonNumericVersions() {
        assertThat(AppVersion.parse("dev")).isNull();
        assertThat(AppVersion.parse("")).isNull();
        assertThat(AppVersion.parse(null)).isNull();
        assertThat(AppVersion.parse("1..2")).isNull();
    }
}
