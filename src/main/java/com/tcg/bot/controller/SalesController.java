package com.tcg.bot.controller;

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
    public String sell(@RequestParam String items, RedirectAttributes redirect) {
        try {
            var ticket = salesService.sell(ReservationController.parseItems(items));
            redirect.addFlashAttribute("saleMessage", "Venta cobrada: " + ticket.units()
                    + (ticket.units() == 1 ? " carta" : " cartas") + " por $ " + ticket.formattedTotal()
                    + ". Ya está en la caja.");
            redirect.addFlashAttribute("clearOrder", true);
        } catch (StockOperationException e) {
            redirect.addFlashAttribute("saleError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo cobrar el carrito.", e);
            redirect.addFlashAttribute("saleError", "No se pudo cobrar: " + e.getMessage());
        }
        return "redirect:/";
    }
}
