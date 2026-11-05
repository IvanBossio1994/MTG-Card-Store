package com.tcg.bot.controller;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.model.PricePoint;
import com.tcg.bot.service.InventoryStore;
import com.tcg.bot.service.PriceHistoryService;
import com.tcg.bot.service.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pantalla "Precios": como fue cambiando el precio de cada carta del inventario. */
@Controller
public class PriceHistoryController {

    private static final Logger log = LoggerFactory.getLogger(PriceHistoryController.class);
    private static final int CHART_WIDTH = 640;
    private static final int CHART_HEIGHT = 200;
    private static final int CHART_PADDING = 12;

    private final InventoryStore store;
    private final PriceHistoryService priceHistoryService;
    private final StoreSettingsService storeSettingsService;

    public PriceHistoryController(
            InventoryStore store,
            PriceHistoryService priceHistoryService,
            StoreSettingsService storeSettingsService
    ) {
        this.store = store;
        this.priceHistoryService = priceHistoryService;
        this.storeSettingsService = storeSettingsService;
    }

    public record Row(String date, String local, String ckUsd, String tcgplayer, String cardmarket) {
    }

    public record Chart(String localLine, String ckLine, String minLabel, String maxLabel,
                        String firstDate, String lastDate, int width, int height) {
    }

    @GetMapping("/precios")
    public String prices(@RequestParam(name = "card", required = false) String cardId, Model model) {
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("hasStoreLogo", storeSettingsService.hasLogo());
        model.addAttribute("selectedCardId", cardId == null ? "" : cardId);

        try {
            var cards = store.getInventoryCards();
            List<PricePoint> all = store.getPriceHistory();
            var trends = priceHistoryService.trends(cards, all);
            model.addAttribute("trends", trends);
            model.addAttribute("hasHistory", !all.isEmpty());

            if (cardId != null && !cardId.isBlank()) {
                var selected = trends.stream().filter(trend -> trend.cardId().equals(cardId)).findFirst().orElse(null);
                List<PricePoint> history = priceHistoryService.history(all, cardId);
                model.addAttribute("selected", selected);
                model.addAttribute("historyRows", rows(history));
                model.addAttribute("chart", chart(history));
            }
        } catch (Exception e) {
            log.warn("No se pudo cargar el historial de precios.", e);
            model.addAttribute("trends", List.of());
            model.addAttribute("hasHistory", false);
            model.addAttribute("priceError", I18n.t("prices.err.read"));
        }

        return "prices";
    }

    static List<Row> rows(List<PricePoint> history) {
        List<Row> rows = new ArrayList<>();
        for (int index = history.size() - 1; index >= 0; index--) {
            PricePoint point = history.get(index);
            rows.add(new Row(
                    displayDate(point.date()),
                    point.localPrice() == null ? "-" : com.tcg.bot.i18n.Money.symbol() + " " + ars(point.localPrice()),
                    point.ckUsd() == null ? "-" : "US$ " + decimal(point.ckUsd()),
                    point.tcgplayerUsd() == null ? "-" : "US$ " + decimal(point.tcgplayerUsd()),
                    point.cardmarketEur() == null ? "-" : "€ " + decimal(point.cardmarketEur())
            ));
        }
        return rows;
    }

    /** Arma las lineas del grafico (SVG) en el servidor: precio local y CK convertido a la misma escala. */
    static Chart chart(List<PricePoint> history) {
        List<PricePoint> points = history.stream().filter(point -> point.localPrice() != null).toList();
        if (points.size() < 2) {
            return null;
        }

        double min = points.stream().mapToDouble(PricePoint::localPrice).min().orElse(0);
        double max = points.stream().mapToDouble(PricePoint::localPrice).max().orElse(0);
        if (max - min < 1) {
            max = min + 1;
        }

        StringBuilder line = new StringBuilder();
        double usable = CHART_WIDTH - CHART_PADDING * 2;
        for (int index = 0; index < points.size(); index++) {
            double x = CHART_PADDING + usable * index / (points.size() - 1);
            double y = CHART_PADDING + (CHART_HEIGHT - CHART_PADDING * 2) * (1 - (points.get(index).localPrice() - min) / (max - min));
            if (index > 0) {
                line.append(' ');
            }
            line.append(String.format(Locale.ROOT, "%.1f,%.1f", x, y));
        }

        return new Chart(line.toString(), "", com.tcg.bot.i18n.Money.symbol() + " " + ars(min), com.tcg.bot.i18n.Money.symbol() + " " + ars(max),
                displayDate(points.get(0).date()), displayDate(points.get(points.size() - 1).date()),
                CHART_WIDTH, CHART_HEIGHT);
    }

    private static String displayDate(String date) {
        try {
            return LocalDate.parse(date.trim()).format(com.tcg.bot.i18n.Dates.shortDate());
        } catch (RuntimeException e) {
            return date;
        }
    }

    static String ars(double value) {
        return com.tcg.bot.i18n.Money.number(value);
    }

    private static String decimal(double value) {
        return com.tcg.bot.i18n.Money.decimal(value);
    }
}
