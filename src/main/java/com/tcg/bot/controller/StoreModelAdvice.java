package com.tcg.bot.controller;

import com.tcg.bot.service.StoreSettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Nombre y logo de la tienda para la barra de arriba de todas las pantallas. */
@ControllerAdvice(annotations = Controller.class)
public class StoreModelAdvice {

    private final StoreSettingsService storeSettingsService;

    public StoreModelAdvice(StoreSettingsService storeSettingsService) {
        this.storeSettingsService = storeSettingsService;
    }

    @ModelAttribute("storeName")
    public String storeName() {
        return storeSettingsService.getStoreName();
    }

    @ModelAttribute("hasStoreLogo")
    public boolean hasStoreLogo() {
        return storeSettingsService.hasLogo();
    }
}
