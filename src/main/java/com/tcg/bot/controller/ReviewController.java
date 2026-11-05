package com.tcg.bot.controller;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.service.ReviewService;
import com.tcg.bot.service.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Pantalla "Para revisar": precios que se movieron, poco stock, cartas sin precio o sin edición. */
@Controller
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final ReviewService reviewService;
    private final StoreSettingsService storeSettingsService;

    public ReviewController(ReviewService reviewService, StoreSettingsService storeSettingsService) {
        this.reviewService = reviewService;
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping("/revisar")
    public String review(Model model) {
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("hasStoreLogo", storeSettingsService.hasLogo());
        model.addAttribute("bigChange", (int) ReviewService.BIG_CHANGE_PERCENT);
        model.addAttribute("salesDays", ReviewService.SALES_DAYS);
        try {
            model.addAttribute("review", reviewService.review());
        } catch (Exception e) {
            log.warn("No se pudo armar la lista para revisar.", e);
            model.addAttribute("reviewError", I18n.t("err.readSheet"));
        }
        return "review";
    }
}
