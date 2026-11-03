package com.tcg.bot.controller;

import com.tcg.bot.model.Game;
import com.tcg.bot.model.GamePrint;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import com.tcg.bot.service.StoreSettingsService;
import com.tcg.bot.service.games.GameInventoryService;
import com.tcg.bot.util.Amounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/** Pestañas de Pokémon y Yu-Gi-Oh!: buscar, stock, ventas y precios. */
@Controller
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    private final GameInventoryService gameInventoryService;
    private final StoreSettingsService storeSettingsService;

    public GameController(GameInventoryService gameInventoryService, StoreSettingsService storeSettingsService) {
        this.gameInventoryService = gameInventoryService;
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping("/juegos/{slug}")
    public String page(@PathVariable String slug, @RequestParam(name = "q", required = false) String query, Model model) {
        Game game = Game.fromSlug(slug);
        if (game == null) {
            return "redirect:/";
        }
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("hasStoreLogo", storeSettingsService.hasLogo());
        model.addAttribute("game", game);
        model.addAttribute("query", query == null ? "" : query);

        if (query != null && !query.isBlank()) {
            try {
                model.addAttribute("results", gameInventoryService.search(game, query));
            } catch (Exception e) {
                log.debug("No se pudo buscar en el catálogo de {}: {}", game.label(), e.getMessage());
                model.addAttribute("results", List.of());
                model.addAttribute("gameError", "No se pudo consultar el catálogo de " + game.label()
                        + ". Revisá la conexión a internet y probá de nuevo.");
            }
        }
        try {
            model.addAttribute("cards", gameInventoryService.cards(game));
        } catch (Exception e) {
            log.warn("No se pudo leer el inventario de {}.", game.label(), e);
            model.addAttribute("cards", List.of());
            model.addAttribute("gameError", "No se pudo leer la pestaña " + game.sheetName()
                    + " del Sheet. Revisá la conexión en Configuración.");
        }
        return "games";
    }

    @PostMapping("/juegos/{slug}/agregar")
    public String add(@PathVariable String slug,
                      @RequestParam String catalogId,
                      @RequestParam String name,
                      @RequestParam(required = false) String setName,
                      @RequestParam(required = false) String setCode,
                      @RequestParam(required = false) String number,
                      @RequestParam(required = false) String rarity,
                      @RequestParam(required = false) String finish,
                      @RequestParam(required = false) String usd,
                      @RequestParam(required = false) String q,
                      RedirectAttributes redirect) {
        Game game = Game.fromSlug(slug);
        if (game == null) {
            return "redirect:/";
        }
        try {
            var card = gameInventoryService.add(game, new GamePrint(catalogId, name, setName, setCode, number, rarity,
                    finish, "", Amounts.parseOrNull(usd), ""));
            redirect.addFlashAttribute("gameMessage", card.getName() + ": ahora hay " + card.getQuantity() + " en stock.");
        } catch (StockOperationException e) {
            redirect.addFlashAttribute("gameError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo agregar la carta de {}.", game.label(), e);
            redirect.addFlashAttribute("gameError", "No se pudo guardar en el Sheet: " + e.getMessage());
        }
        return back(game, q);
    }

    @PostMapping("/juegos/{slug}/stock")
    public String change(@PathVariable String slug,
                         @RequestParam String id,
                         @RequestParam int change,
                         @RequestParam(defaultValue = "sale") String reason,
                         @RequestParam(required = false) String q,
                         RedirectAttributes redirect) {
        Game game = Game.fromSlug(slug);
        if (game == null) {
            return "redirect:/";
        }
        try {
            boolean sale = "sale".equals(reason) && change < 0;
            var card = gameInventoryService.change(game, id, change > 0 ? 1 : -1, sale);
            redirect.addFlashAttribute("gameMessage", sale
                    ? "Vendida " + card.getName() + " por " + card.formattedLocalPrice() + ". Ya está en la caja."
                    : card.getName() + ": ahora hay " + card.getQuantity() + " en stock.");
        } catch (StockOperationException e) {
            redirect.addFlashAttribute("gameError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo cambiar el stock de {}.", game.label(), e);
            redirect.addFlashAttribute("gameError", "No se pudo guardar en el Sheet: " + e.getMessage());
        }
        return back(game, q);
    }

    @PostMapping("/juegos/{slug}/precios")
    public String refresh(@PathVariable String slug, RedirectAttributes redirect) {
        Game game = Game.fromSlug(slug);
        if (game == null) {
            return "redirect:/";
        }
        try {
            int changed = gameInventoryService.refreshPrices(game);
            redirect.addFlashAttribute("gameMessage", changed == 0
                    ? "Precios al día: no cambió ninguno."
                    : "Se actualizaron los precios de " + changed + (changed == 1 ? " carta." : " cartas."));
        } catch (Exception e) {
            log.warn("No se pudieron actualizar los precios de {}.", game.label(), e);
            redirect.addFlashAttribute("gameError", "No se pudieron actualizar los precios: " + e.getMessage());
        }
        return back(game, null);
    }

    private static String back(Game game, String query) {
        var builder = UriComponentsBuilder.fromPath("/juegos/" + game.slug());
        if (query != null && !query.isBlank()) {
            builder.queryParam("q", query);
        }
        return "redirect:" + builder.encode().build().toUriString();
    }
}
