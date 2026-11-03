package com.tcg.bot.controller;

import com.tcg.bot.model.InventoryCard;
import com.tcg.bot.service.CardIdentifyService;
import com.tcg.bot.service.InventoryStore;
import com.tcg.bot.service.StockOperationsService;
import com.tcg.bot.service.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Objects;

/**
 * Pantalla "Completar ediciones": cartas del Sheet a las que les falta edición o número, y por eso
 * no consiguen precio de Card Kingdom. La persona busca y elige la edición correcta; la app nunca adivina.
 */
@Controller
public class IdentifyController {

    private static final Logger log = LoggerFactory.getLogger(IdentifyController.class);

    private final InventoryStore store;
    private final CardIdentifyService identifyService;
    private final StoreSettingsService storeSettingsService;

    public IdentifyController(InventoryStore store, CardIdentifyService identifyService,
                              StoreSettingsService storeSettingsService) {
        this.store = store;
        this.identifyService = identifyService;
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping("/completar")
    public String page(@RequestParam(name = "row", required = false) Integer row,
                       @RequestParam(name = "buscar", required = false) String searchName,
                       Model model) {
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("hasStoreLogo", storeSettingsService.hasLogo());

        List<InventoryCard> pending;
        try {
            pending = store.getInventoryCards().stream().filter(CardIdentifyService::needsIdentification).toList();
        } catch (Exception e) {
            log.warn("No se pudo leer el inventario para completar ediciones.", e);
            model.addAttribute("pending", List.of());
            model.addAttribute("identifyError", "No se pudo leer el Sheet. Revisá la conexión en Configuración.");
            return "identify";
        }
        model.addAttribute("pending", pending);

        if (row != null) {
            InventoryCard selected = pending.stream().filter(card -> card.getRowIndex() == row).findFirst().orElse(null);
            if (selected != null) {
                String name = searchName == null || searchName.isBlank() ? selected.getName() : searchName.trim();
                model.addAttribute("selected", selected);
                model.addAttribute("searchName", name);
                try {
                    model.addAttribute("candidates", identifyService.candidates(name));
                } catch (Exception e) {
                    log.debug("Scryfall no respondió: {}", e.getMessage());
                    model.addAttribute("candidates", List.of());
                    model.addAttribute("identifyError", "No se pudo consultar Scryfall. Revisá la conexión a internet y probá de nuevo.");
                }
            }
        }

        return "identify";
    }

    @PostMapping("/completar")
    public String complete(@RequestParam("row") int row,
                           @RequestParam("originalName") String originalName,
                           @RequestParam("name") String name,
                           @RequestParam("setCode") String setCode,
                           @RequestParam("setName") String setName,
                           @RequestParam("collectorNumber") String collectorNumber,
                           @RequestParam(name = "finish", required = false) String finish,
                           RedirectAttributes redirect) {
        try {
            synchronized (StockOperationsService.writeLock()) {
                InventoryCard card = store.getInventoryCards().stream()
                        .filter(candidate -> candidate.getRowIndex() == row)
                        .findFirst()
                        .orElse(null);

                // Si el Sheet cambió mientras se elegía (se ordenó o se editó la fila), no se pisa otra carta.
                if (card == null || !Objects.equals(clean(card.getName()), clean(originalName))) {
                    redirect.addFlashAttribute("identifyError",
                            "La fila cambió en el Sheet mientras elegías. Volvé a buscar la carta.");
                    return "redirect:/completar";
                }

                card.setName(name.trim());
                card.setSetCode(setCode.trim());
                card.setSetName(setName.trim());
                card.setCollectorNumber(collectorNumber.trim());
                if ((card.getPrinting() == null || card.getPrinting().isBlank()) && finish != null && !finish.isBlank()) {
                    card.setPrinting(finish.trim());
                }
                store.updateCardIdentity(row, card);
            }
        } catch (Exception e) {
            log.warn("No se pudo completar la edición de la fila {}.", row, e);
            redirect.addFlashAttribute("identifyError", "No se pudo guardar en el Sheet. Probá de nuevo.");
            return "redirect:/completar";
        }

        redirect.addFlashAttribute("identifySuccess", name.trim() + " quedó como " + setName.trim()
                + " #" + collectorNumber.trim() + ". Sincronizá para traer su precio.");
        return "redirect:/completar";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
