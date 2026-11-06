package com.tcg.bot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LicenseServiceTest {

    @TempDir
    Path dir;

    private final AtomicReference<Object> next = new AtomicReference<>();
    private final AtomicReference<String> google = new AtomicReference<>("tienda@example.com");

    private LicenseService at(Instant now, String productId) {
        return new LicenseService(dir.resolve("license.properties"), key -> {
            Object value = next.get();
            if (value instanceof Exception e) {
                throw e;
            }
            return (LicenseService.Validation) value;
        }, Clock.fixed(now, ZoneOffset.UTC), productId, "https://tienda.lemonsqueezy.com/buy/abc", google::get);
    }

    private static LicenseService.Validation active(String productId) {
        return new LicenseService.Validation(true, "active", "", "tienda@example.com", productId, null);
    }

    @Test
    void freePlanLimitsNewCards() {
        LicenseService service = at(Instant.parse("2026-11-03T00:00:00Z"), "");
        assertThat(service.isPro()).isFalse();
        assertThat(service.remainingNewCards(250)).isEqualTo(50);
        assertThat(service.remainingNewCards(400)).isZero();
    }

    @Test
    void activeLicenseUnlocks() throws Exception {
        next.set(active("123"));
        LicenseService service = at(Instant.parse("2026-11-03T00:00:00Z"), "123");

        var status = service.activate("ABCD-EFGH-1234");

        assertThat(status.pro()).isTrue();
        assertThat(status.email()).isEqualTo("tienda@example.com");
        assertThat(service.remainingNewCards(5000)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void keyFromAnotherProductIsRejected() throws Exception {
        next.set(active("999"));
        LicenseService service = at(Instant.parse("2026-11-03T00:00:00Z"), "123");

        assertThat(service.activate("ABCD-EFGH-1234").pro()).isFalse();
    }

    @Test
    void newlyBoughtInactiveKeyUnlocks() throws Exception {
        next.set(new LicenseService.Validation(true, "inactive", "", "", "123", null));
        LicenseService service = at(Instant.parse("2026-11-03T00:00:00Z"), "123");
        service.activate("ABCD-EFGH-1234");
        assertThat(service.isPro()).isTrue();
    }

    @Test
    void expiredLicenseIsFree() throws Exception {
        next.set(new LicenseService.Validation(true, "expired", "", "", "123", null));
        LicenseService service = at(Instant.parse("2026-11-03T00:00:00Z"), "123");

        var status = service.activate("ABCD-EFGH-1234");

        assertThat(status.pro()).isFalse();
        assertThat(status.message()).contains("vencida");
    }

    @Test
    void worksOfflineDuringGraceThenFallsBackToFree() throws Exception {
        next.set(active("123"));
        at(Instant.parse("2026-11-03T00:00:00Z"), "123").activate("ABCD-EFGH-1234");

        next.set(new java.io.IOException("sin internet"));
        LicenseService fiveDaysLater = at(Instant.parse("2026-11-08T00:00:00Z"), "123");
        fiveDaysLater.refresh();
        assertThat(fiveDaysLater.isPro()).isTrue();

        LicenseService tenDaysLater = at(Instant.parse("2026-11-13T00:00:00Z"), "123");
        tenDaysLater.refresh();
        assertThat(tenDaysLater.isPro()).isFalse();
    }

    @Test
    void parsesLemonSqueezyResponse() {
        var validation = LicenseService.parse("""
                {"valid":true,"error":null,
                 "license_key":{"id":1,"status":"active","key":"x","expires_at":null},
                 "instance":null,
                 "meta":{"store_id":1,"product_id":123,"customer_email":"a@b.com"}}
                """);

        assertThat(validation.valid()).isTrue();
        assertThat(validation.status()).isEqualTo("active");
        assertThat(validation.productId()).isEqualTo("123");
        assertThat(validation.email()).isEqualTo("a@b.com");
    }

    @Test
    void licenseOnlyWorksWithTheBuyersGoogleAccount() throws Exception {
        next.set(active("123"));
        LicenseService service = at(Instant.parse("2026-11-03T00:00:00Z"), "123");
        assertThat(service.activate("ABCD-EFGH-1234").pro()).isTrue();

        google.set("Tienda@Example.com");
        assertThat(service.isPro()).as("mail sin importar mayusculas").isTrue();

        google.set("otra@example.com");
        assertThat(service.isPro()).isFalse();
        assertThat(service.status().email()).isEqualTo("tienda@example.com");

        google.set("");
        assertThat(service.isPro()).isFalse();
        assertThat(service.status().email()).isEqualTo("tienda@example.com");
    }

    @Test
    void purchaseLinkCarriesTheGoogleEmail() {
        LicenseService service = at(Instant.parse("2026-11-03T00:00:00Z"), "");
        assertThat(service.getPurchaseUrl())
                .isEqualTo("https://tienda.lemonsqueezy.com/buy/abc?checkout%5Bemail%5D=tienda%40example.com");

        google.set("");
        assertThat(service.getPurchaseUrl()).isEqualTo("https://tienda.lemonsqueezy.com/buy/abc");
    }
}
