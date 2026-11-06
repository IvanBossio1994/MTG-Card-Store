package com.tcg.bot.controller;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.service.DeckCheckService;
import com.tcg.bot.service.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** "Pegar un mazo": qué cartas de la lista del cliente hay en stock y cuánto sale. */
@Controller
public class DeckController {

    private static final Logger log = LoggerFactory.getLogger(DeckController.class);
    static final int MAX_LIST_LENGTH = 20_000;
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    private final DeckCheckService deckCheckService;
    private final StoreSettingsService storeSettingsService;

    public DeckController(DeckCheckService deckCheckService, StoreSettingsService storeSettingsService) {
        this.deckCheckService = deckCheckService;
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping("/mazo")
    public String page(Model model) {
        addBase(model);
        return "deck";
    }

    @PostMapping("/mazo")
    public String check(@RequestParam(defaultValue = "") String deckList, Model model) {
        addBase(model);
        String list = deckList.length() > MAX_LIST_LENGTH ? deckList.substring(0, MAX_LIST_LENGTH) : deckList;
        model.addAttribute("deckList", list);
        if (list.isBlank()) {
            model.addAttribute("deckError", I18n.t("deck.err.empty"));
            return "deck";
        }
        try {
            DeckCheckService.Result result = deckCheckService.check(list);
            model.addAttribute("deck", result);
            List<Map<String, Object>> items = cartItems(result);
            model.addAttribute("deckItems", items);
            model.addAttribute("deckItemsJson", JSON.writeValueAsString(items));
        } catch (Exception e) {
            log.warn("No se pudo revisar el mazo.", e);
            model.addAttribute("deckError", I18n.t("deck.err.read"));
        }
        return "deck";
    }

    /** Lo encontrado, con el formato del carrito (localStorage) para sumarlo con un botón. */
    static List<Map<String, Object>> cartItems(DeckCheckService.Result result) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (DeckCheckService.Line line : result.lines()) {
            for (DeckCheckService.Pick pick : line.picks()) {
                var card = pick.card();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("cardId", card.getId());
                item.put("quantity", pick.quantity());
                item.put("name", card.getName());
                item.put("set", card.getGame() == null ? pick.setLabel() : card.getSetName());
                item.put("price", card.localPriceValue());
                item.put("stock", com.tcg.bot.util.Amounts.parseInt(card.getQuantity()));
                item.put("manual", card.getGame() == null ? card.isManualPrice() : false);
                items.add(item);
            }
        }
        return items;
    }

    private void addBase(Model model) {
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("hasStoreLogo", storeSettingsService.hasLogo());
    }
}
