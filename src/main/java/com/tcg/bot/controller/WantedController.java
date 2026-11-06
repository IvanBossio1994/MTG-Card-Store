package com.tcg.bot.controller;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.model.WantedCard;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import com.tcg.bot.service.StoreSettingsService;
import com.tcg.bot.service.WantedService;
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

/** Cartas buscadas por clientes y aviso por WhatsApp cuando entran. */
@Controller
public class WantedController {

    private static final Logger log = LoggerFactory.getLogger(WantedController.class);

    private final WantedService wantedService;
    private final StoreSettingsService storeSettingsService;

    public WantedController(WantedService wantedService, StoreSettingsService storeSettingsService) {
        this.wantedService = wantedService;
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping("/buscadas")
    public String page(Model model) {
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("hasStoreLogo", storeSettingsService.hasLogo());
        try {
            List<WantedService.View> views = wantedService.open();
            Map<String, String> links = new HashMap<>();
            for (WantedService.View view : views) {
                if (view.arrived()) {
                    links.put(view.wanted().getId(), whatsappLink(view, storeSettingsService.getStoreName()));
                }
            }
            model.addAttribute("arrived", views.stream().filter(WantedService.View::arrived).toList());
            model.addAttribute("waiting", views.stream().filter(view -> !view.arrived()).toList());
            model.addAttribute("whatsappLinks", links);
        } catch (Exception e) {
            log.warn("No se pudieron leer las cartas buscadas.", e);
            model.addAttribute("arrived", List.of());
            model.addAttribute("waiting", List.of());
            model.addAttribute("whatsappLinks", Map.of());
            model.addAttribute("wantedError", I18n.t("wanted.err.read"));
        }
        return "wanted";
    }

    @PostMapping("/buscadas")
    public String add(@RequestParam(defaultValue = "") String customer,
                      @RequestParam(defaultValue = "") String contact,
                      @RequestParam(defaultValue = "") String cards,
                      RedirectAttributes redirect) {
        try {
            List<WantedCard> added = wantedService.add(customer, contact, cards);
            redirect.addFlashAttribute("wantedMessage", I18n.t("wanted.added", added.size(), customer.trim()));
        } catch (StockOperationException e) {
            redirect.addFlashAttribute("wantedError", e.getMessage());
            redirect.addFlashAttribute("draftCustomer", customer);
            redirect.addFlashAttribute("draftContact", contact);
            redirect.addFlashAttribute("draftCards", cards);
        } catch (Exception e) {
            log.warn("No se pudieron anotar las cartas buscadas.", e);
            redirect.addFlashAttribute("wantedError", I18n.t("err.saveSheet", e.getMessage()));
        }
        return "redirect:/buscadas";
    }

    @PostMapping("/buscadas/avisado")
    public String notified(@RequestParam String id, RedirectAttributes redirect) {
        return change(id, redirect, true);
    }

    @PostMapping("/buscadas/cerrar")
    public String close(@RequestParam String id, RedirectAttributes redirect) {
        return change(id, redirect, false);
    }

    private String change(String id, RedirectAttributes redirect, boolean notified) {
        try {
            WantedCard card = notified ? wantedService.markNotified(id) : wantedService.close(id);
            redirect.addFlashAttribute("wantedMessage",
                    I18n.t(notified ? "wanted.notifiedDone" : "wanted.closedDone", card.getName(), card.getCustomer()));
        } catch (StockOperationException e) {
            redirect.addFlashAttribute("wantedError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo actualizar la carta buscada.", e);
            redirect.addFlashAttribute("wantedError", I18n.t("err.saveSheet", e.getMessage()));
        }
        return "redirect:/buscadas";
    }

    static String whatsappLink(WantedService.View view, String storeName) {
        WantedCard wanted = view.wanted();
        StringBuilder text = new StringBuilder(I18n.t("res.wa.hello", wanted.getCustomer())).append(" ");
        if (storeName != null && !storeName.isBlank()) {
            text.append(I18n.t("res.wa.from", storeName.trim())).append(" ");
        }
        text.append(I18n.t("wanted.wa.arrived", Math.min(wanted.getQuantity(), view.available()), wanted.getName()));
        return com.tcg.bot.util.Whatsapp.link(wanted.getContact(), text.toString());
    }
}
