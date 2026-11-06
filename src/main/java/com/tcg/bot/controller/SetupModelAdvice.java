package com.tcg.bot.controller;

import com.tcg.bot.service.InventoryService;
import com.tcg.bot.service.StoreSettingsService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Avisa a todas las pantallas si todavía no hay Sheet conectado. Así, en una instalación nueva,
 * se ve un solo aviso amable con el botón a Configuración en vez de varios errores en rojo.
 */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
public class SetupModelAdvice {

    private final StoreSettingsService storeSettingsService;
    private final InventoryService inventoryService;

    public SetupModelAdvice(StoreSettingsService storeSettingsService, InventoryService inventoryService) {
        this.storeSettingsService = storeSettingsService;
        this.inventoryService = inventoryService;
    }

    @ModelAttribute("setupPending")
    public boolean setupPending() {
        return !storeSettingsService.hasSpreadsheetConfigured() || !inventoryService.hasCredentialsConfigured();
    }
}
