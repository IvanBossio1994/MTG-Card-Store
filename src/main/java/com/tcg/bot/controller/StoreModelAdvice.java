package com.tcg.bot.controller;

import com.tcg.bot.service.StoreSettingsService;
import com.tcg.bot.service.StoreTheme;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Nombre, logo y decoración de la tienda (fondo y colores del logo) para todas las pantallas. */
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

    /** Se suma a la dirección del logo para que, al cambiarlo, el navegador no muestre el viejo. */
    @ModelAttribute("storeLogoVersion")
    public long storeLogoVersion() {
        return storeSettingsService.getLogoVersion();
    }

    /** "none", "watermark" o "pattern". Sin logo no hay nada que poner de fondo. */
    @ModelAttribute("storeBackdrop")
    public String storeBackdrop() {
        return storeSettingsService.hasLogo() ? storeSettingsService.getBackdropStyle() : StoreSettingsService.BACKDROP_NONE;
    }

    /** Opacidad del logo de fondo: de 0.02 a 0.20, siempre suave para no tapar nada. */
    @ModelAttribute("storeBackdropOpacity")
    public String storeBackdropOpacity() {
        return backdropOpacity(storeSettingsService.getBackdropIntensity());
    }

    /** La opción elegida en Configuración, aunque todavía no haya logo. */
    @ModelAttribute("storeBackdropStyle")
    public String storeBackdropStyle() {
        return storeSettingsService.getBackdropStyle();
    }

    @ModelAttribute("storeBackdropIntensity")
    public int storeBackdropIntensity() {
        return storeSettingsService.getBackdropIntensity();
    }

    @ModelAttribute("storeUseLogoColors")
    public boolean storeUseLogoColors() {
        return storeSettingsService.isUseLogoColors();
    }

    @ModelAttribute("storeLogoAccent")
    public String storeLogoAccent() {
        return storeSettingsService.getLogoAccent();
    }

    /** Colores del logo para botones y detalles, ya oscurecidos para que el texto blanco se lea. "" = los de siempre. */
    @ModelAttribute("storeAccent")
    public String storeAccent() {
        return activeAccent() ? StoreTheme.readableAccent(storeSettingsService.getLogoAccent()) : "";
    }

    @ModelAttribute("storeAccent2")
    public String storeAccent2() {
        return activeAccent() ? StoreTheme.secondAccent(storeSettingsService.getLogoAccent()) : "";
    }

    private boolean activeAccent() {
        return storeSettingsService.isUseLogoColors()
                && storeSettingsService.hasLogo()
                && !storeSettingsService.getLogoAccent().isEmpty();
    }

    static String backdropOpacity(int intensity) {
        return String.format(java.util.Locale.ROOT, "%.2f", intensity * 0.02);
    }
}
