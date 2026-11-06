package com.tcg.bot.controller;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.service.ReportService;
import com.tcg.bot.service.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** Reportes: más vendidas, cartas quietas, valor del stock y mejores clientes. */
@Controller
public class ReportController {

    static final List<Integer> PERIODS = List.of(7, 30, 90, 365);

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;
    private final StoreSettingsService storeSettingsService;

    public ReportController(ReportService reportService, StoreSettingsService storeSettingsService) {
        this.reportService = reportService;
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping("/reportes")
    public String page(@RequestParam(name = "dias", defaultValue = "30") int days, Model model) {
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("hasStoreLogo", storeSettingsService.hasLogo());
        int period = PERIODS.contains(days) ? days : 30;
        model.addAttribute("periods", PERIODS);
        model.addAttribute("period", period);
        model.addAttribute("stillDays", ReportService.STILL_DAYS);
        try {
            model.addAttribute("report", reportService.report(period));
        } catch (Exception e) {
            log.warn("No se pudieron armar los reportes.", e);
            model.addAttribute("reportError", I18n.t("report.err.read"));
        }
        return "reports";
    }
}
