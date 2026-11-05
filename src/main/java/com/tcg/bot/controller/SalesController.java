package com.tcg.bot.controller;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.service.SalesService;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Cobrar el carrito armado en la búsqueda: todas las cartas juntas, en el momento. */
@Controller
public class SalesController {

    private static final Logger log = LoggerFactory.getLogger(SalesController.class);

    private final SalesService salesService;

    public SalesController(SalesService salesService) {
        this.salesService = salesService;
    }

    @PostMapping("/ventas")
    public String sell(@RequestParam String items,
                       @RequestParam(required = false) String creditCustomer,
                       RedirectAttributes redirect) {
        try {
            var ticket = salesService.sell(ReservationController.parseItems(items), creditCustomer);
            String message = I18n.t("sale.done", ticket.units(), com.tcg.bot.i18n.Money.symbol() + " " + ticket.formattedTotal());
            if (ticket.creditUsed() > 0) {
                message += " " + I18n.t("sale.creditUsed", com.tcg.bot.i18n.Money.symbol() + " " + ticket.formattedCreditUsed(), creditCustomer.trim(),
                        com.tcg.bot.i18n.Money.symbol() + " " + ticket.formattedToPay());
            } else if (creditCustomer != null && !creditCustomer.isBlank()) {
                message += " " + I18n.t("sale.noCredit", creditCustomer.trim());
            }
            redirect.addFlashAttribute("saleMessage", message);
            redirect.addFlashAttribute("clearOrder", true);
        } catch (StockOperationException e) {
            redirect.addFlashAttribute("saleError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo cobrar el carrito.", e);
            redirect.addFlashAttribute("saleError", I18n.t("sale.err", e.getMessage()));
        }
        return "redirect:/";
    }
}
