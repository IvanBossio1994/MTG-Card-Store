package com.tcg.bot.controller;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.i18n.Money;
import com.tcg.bot.service.ConsignmentService;
import com.tcg.bot.service.StockOperationsService;
import com.tcg.bot.service.StoreSettingsService;
import com.tcg.bot.util.Whatsapp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Consignación: lo que los clientes dejaron para vender, lo vendido y lo que se les debe. */
@Controller
public class ConsignmentController {

    private static final Logger log = LoggerFactory.getLogger(ConsignmentController.class);

    private final ConsignmentService consignmentService;
    private final StoreSettingsService storeSettingsService;

    public ConsignmentController(ConsignmentService consignmentService, StoreSettingsService storeSettingsService) {
        this.consignmentService = consignmentService;
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping("/consignacion")
    public String page(Model model) {
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("hasStoreLogo", storeSettingsService.hasLogo());
        List<ConsignmentService.Owner> owners = List.of();
        try {
            owners = consignmentService.owners();
        } catch (Exception e) {
            log.warn("No se pudo leer la consignación.", e);
            model.addAttribute("consignError", I18n.t("err.readSheet"));
        }
        Map<String, String> whatsapp = new HashMap<>();
        for (var owner : owners) {
            if (owner.owed() > 0) {
                String link = Whatsapp.link(owner.contact(), I18n.t("consign.whatsapp", owner.name(), owner.sold(),
                        Money.format(owner.owed())));
                if (link != null) {
                    whatsapp.put(owner.name(), link);
                }
            }
        }
        model.addAttribute("owners", owners);
        model.addAttribute("whatsappLinks", whatsapp);
        model.addAttribute("totalOwed", Money.format(owners.stream().mapToDouble(ConsignmentService.Owner::owed).sum()));
        return "consignment";
    }

    @PostMapping("/consignacion/pagar")
    public String pay(@RequestParam String owner, @RequestParam(defaultValue = "cash") String payout,
                      RedirectAttributes redirect) {
        try {
            boolean credit = "credit".equalsIgnoreCase(payout);
            double paid = consignmentService.pay(owner, credit ? ConsignmentService.Payout.CREDIT : ConsignmentService.Payout.CASH);
            redirect.addFlashAttribute("consignMessage",
                    I18n.t(credit ? "consign.paid.credit" : "consign.paid.cash", Money.format(paid), owner.trim()));
        } catch (StockOperationsService.StockOperationException e) {
            redirect.addFlashAttribute("consignError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo pagar la consignación de {}.", owner, e);
            redirect.addFlashAttribute("consignError", I18n.t("err.saveSheet", e.getMessage()));
        }
        return "redirect:/consignacion";
    }

    @PostMapping("/consignacion/devolver")
    public String giveBack(@RequestParam String id, RedirectAttributes redirect) {
        try {
            int units = consignmentService.giveBack(id);
            redirect.addFlashAttribute("consignMessage", I18n.t("consign.returned", units));
        } catch (StockOperationsService.StockOperationException e) {
            redirect.addFlashAttribute("consignError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo devolver la consignación {}.", id, e);
            redirect.addFlashAttribute("consignError", I18n.t("err.saveSheet", e.getMessage()));
        }
        return "redirect:/consignacion";
    }
}
