package com.tcg.bot.controller;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.service.PublicCatalogService;
import com.tcg.bot.service.StoreSettingsService;
import com.tcg.bot.util.Amounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Catálogo y buylist públicos: un Sheet de solo lectura para compartir por WhatsApp o redes. */
@Controller
public class PublicCatalogController {

    private static final Logger log = LoggerFactory.getLogger(PublicCatalogController.class);

    private final PublicCatalogService catalog;
    private final StoreSettingsService storeSettingsService;

    public PublicCatalogController(PublicCatalogService catalog, StoreSettingsService storeSettingsService) {
        this.catalog = catalog;
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping("/publico")
    public String page(Model model) {
        var settings = catalog.settings();
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("hasStoreLogo", storeSettingsService.hasLogo());
        model.addAttribute("settings", settings);
        model.addAttribute("available", catalog.available());
        if (settings.published()) {
            String text = I18n.t("public.share.text", settings.link());
            model.addAttribute("whatsappShare", "https://wa.me/?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8));
        }
        return "public-catalog";
    }

    @PostMapping("/publico")
    public String publish(@RequestParam(defaultValue = "50") int cashPercent,
                          @RequestParam(defaultValue = "60") int creditPercent,
                          @RequestParam(defaultValue = "4") int target,
                          @RequestParam(defaultValue = "0") String minPrice,
                          RedirectAttributes redirect) {
        try {
            catalog.saveSettings(cashPercent, creditPercent, target, Amounts.parse(minPrice));
            var result = catalog.publish();
            redirect.addFlashAttribute("publicMessage",
                    I18n.t("public.published", result.catalogCards(), result.buylistCards()));
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("publicError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo publicar el catálogo.", e);
            redirect.addFlashAttribute("publicError", I18n.t("public.err", e.getMessage()));
        }
        return "redirect:/publico";
    }
}
