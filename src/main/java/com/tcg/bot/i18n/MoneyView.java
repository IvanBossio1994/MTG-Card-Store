package com.tcg.bot.i18n;

import com.tcg.bot.service.PricingSettingsService;

/** Acceso a {@link Money} desde las plantillas como ${money.number(x)}. */
public final class MoneyView {

    public String number(double amount) {
        return Money.number(amount);
    }

    public String plainNumber(double value) {
        return Money.plainNumber(value);
    }

    public double suggestedRoundMultiple(String currency) {
        return PricingSettingsService.suggestedRoundMultiple(currency);
    }
}
