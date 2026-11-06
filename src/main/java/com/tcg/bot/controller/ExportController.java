package com.tcg.bot.controller;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.model.Game;
import com.tcg.bot.service.StockExportService;
import com.tcg.bot.service.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Exportar el stock a CSV para subirlo a Moxfield, ManaBox, TCG PowerTools (Cardmarket, CardTrader) y otros. */
@Controller
public class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    private final StockExportService exportService;
    private final StoreSettingsService storeSettingsService;

    public ExportController(StockExportService exportService, StoreSettingsService storeSettingsService) {
        this.exportService = exportService;
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping("/exportar")
    public String page(Model model) {
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("hasStoreLogo", storeSettingsService.hasLogo());
        model.addAttribute("games", Game.values());
        return "export";
    }

    @GetMapping("/exportar/stock.csv")
    public ResponseEntity<byte[]> csv(@RequestParam(defaultValue = "magic") String game) {
        try {
            Game other = "magic".equalsIgnoreCase(game) ? null : Game.fromSlug(game);
            String csv = other == null ? exportService.magicCsv() : exportService.gameCsv(other);
            String file = "stock-" + (other == null ? "magic" : other.slug()) + "-" + LocalDate.now() + ".csv";
            // Con BOM, Excel abre bien los acentos.
            byte[] body = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"")
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .body(body);
        } catch (Exception e) {
            log.warn("No se pudo exportar el stock.", e);
            return ResponseEntity.internalServerError()
                    .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .body(I18n.t("export.err", e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }
}
