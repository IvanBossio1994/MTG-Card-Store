package com.tcg.bot.controller;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.service.CashCloseService;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import com.tcg.bot.util.Amounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Pantalla de cierre de caja del día, pensada también para imprimir. */
@Controller
public class CashCloseController {

    private static final Logger log = LoggerFactory.getLogger(CashCloseController.class);

    private final CashCloseService cashCloseService;

    public CashCloseController(CashCloseService cashCloseService) {
        this.cashCloseService = cashCloseService;
    }

    @GetMapping("/caja/cierre")
    public String page(@RequestParam(name = "fecha", required = false) String date, Model model) {
        if (date != null && !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            date = null;
        }
        try {
            model.addAttribute("day", cashCloseService.day(date));
        } catch (Exception e) {
            log.warn("No se pudo armar el cierre de caja.", e);
            model.addAttribute("closeError", I18n.t("close.err.read"));
        }
        return "cash-close";
    }

    @PostMapping("/caja/cierre")
    public String close(@RequestParam String fecha,
                        @RequestParam(defaultValue = "0") String efectivo,
                        @RequestParam(defaultValue = "0") String transferencias,
                        @RequestParam(defaultValue = "0") String tarjeta,
                        @RequestParam(required = false) String nota,
                        RedirectAttributes redirect) {
        if (fecha == null || !fecha.matches("\\d{4}-\\d{2}-\\d{2}")) {
            redirect.addFlashAttribute("closeError", I18n.t("close.err.date"));
            return "redirect:/caja/cierre";
        }
        try {
            double difference = cashCloseService.close(fecha, Amounts.parse(efectivo), Amounts.parse(transferencias),
                    Amounts.parse(tarjeta), nota);
            redirect.addFlashAttribute("closeMessage", com.tcg.bot.i18n.Money.isZero(difference)
                    ? I18n.t("close.done.match")
                    : I18n.t("close.done.diff", CashCloseService.signed(difference)));
        } catch (StockOperationException e) {
            redirect.addFlashAttribute("closeError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo guardar el cierre de caja.", e);
            redirect.addFlashAttribute("closeError", I18n.t("close.err.save", e.getMessage()));
        }
        return "redirect:/caja/cierre?fecha=" + fecha;
    }
}
