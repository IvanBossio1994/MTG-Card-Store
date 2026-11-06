package com.tcg.bot.controller;

import com.tcg.bot.i18n.I18n;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tcg.bot.service.ReservationService;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import com.tcg.bot.service.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
public class ReservationController {

    private static final Logger log = LoggerFactory.getLogger(ReservationController.class);

    private final ReservationService reservationService;
    private final StoreSettingsService storeSettingsService;

    public ReservationController(
            ReservationService reservationService,
            StoreSettingsService storeSettingsService
    ) {
        this.reservationService = reservationService;
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping("/reservas")
    public String reservations(Model model) {
        model.addAttribute("defaultDays", ReservationService.DEFAULT_DAYS);

        try {
            int expired = reservationService.releaseExpired();
            if (expired > 0 && !model.containsAttribute("reservationMessage")) {
                model.addAttribute("reservationMessage", expired == 1
                        ? I18n.t("res.expiredOne")
                        : I18n.t("res.expiredMany", expired));
            }
            var orders = reservationService.orders();
            model.addAttribute("orders", orders);
            java.util.Map<String, String> whatsapp = new java.util.HashMap<>();
            for (var order : orders) {
                if (order.isActive()) {
                    whatsapp.put(order.reservationId(), whatsappLink(order, storeSettingsService.getStoreName()));
                }
            }
            model.addAttribute("whatsappLinks", whatsapp);
        } catch (Exception e) {
            log.warn("No se pudieron cargar las reservas.", e);
            model.addAttribute("orders", List.of());
            if (!model.containsAttribute("reservationError")) {
                model.addAttribute("reservationError", I18n.t("res.err.load"));
            }
        }

        return "reservations";
    }

    @PostMapping("/reservas")
    public String create(
            @RequestParam String customer,
            @RequestParam(required = false) String contact,
            @RequestParam(defaultValue = "7") int days,
            @RequestParam String items,
            RedirectAttributes redirectAttributes
    ) {
        try {
            String reservationId = reservationService.create(customer, contact, days, parseItems(items));
            redirectAttributes.addFlashAttribute("reservationMessage",
                    I18n.t("res.created", reservationId, customer.trim()));
            redirectAttributes.addFlashAttribute("clearOrder", true);
        } catch (StockOperationException e) {
            redirectAttributes.addFlashAttribute("reservationError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo crear la reserva.", e);
            redirectAttributes.addFlashAttribute("reservationError", I18n.t("res.err.create", e.getMessage()));
        }

        return "redirect:/reservas";
    }

    @PostMapping("/reservas/{id}/retirar")
    public String pickUp(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            var order = reservationService.pickUp(id);
            redirectAttributes.addFlashAttribute("reservationMessage",
                    I18n.t("res.pickedUpDone", order.customer(), com.tcg.bot.i18n.Money.symbol() + " " + order.formattedTotal()));
        } catch (StockOperationException e) {
            redirectAttributes.addFlashAttribute("reservationError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo registrar el retiro de {}.", id, e);
            redirectAttributes.addFlashAttribute("reservationError", I18n.t("res.err.pickup", e.getMessage()));
        }

        return "redirect:/reservas";
    }

    @PostMapping("/reservas/{id}/cancelar")
    public String cancel(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            var order = reservationService.cancel(id);
            redirectAttributes.addFlashAttribute("reservationMessage",
                    I18n.t("res.cancelled", order.customer()));
        } catch (StockOperationException e) {
            redirectAttributes.addFlashAttribute("reservationError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo cancelar la reserva {}.", id, e);
            redirectAttributes.addFlashAttribute("reservationError", I18n.t("res.err.cancel", e.getMessage()));
        }

        return "redirect:/reservas";
    }

    /** Link de WhatsApp con el aviso de la reserva ya escrito. */
    static String whatsappLink(ReservationService.Order order, String storeName) {
        StringBuilder text = new StringBuilder(I18n.t("res.wa.hello", order.customer())).append(" ");
        if (storeName != null && !storeName.isBlank()) {
            text.append(I18n.t("res.wa.from", storeName.trim())).append(" ");
        }
        text.append(I18n.t("res.wa.until", order.formattedExpiresOn())).append("\n");
        for (var line : order.lines()) {
            text.append("• ").append(line.getQuantity()).append(" × ").append(line.getName()).append("\n");
        }
        text.append(I18n.t("res.wa.total", com.tcg.bot.i18n.Money.symbol() + " " + order.formattedTotal()));

        return com.tcg.bot.util.Whatsapp.link(order.contact(), text.toString());
    }

    /** items llega como JSON: [{"cardId":"abc","quantity":2}, ...] desde el pedido armado en la búsqueda. */
    static List<ReservationService.Item> parseItems(String json) throws StockOperationException {
        List<ReservationService.Item> items = new ArrayList<>();
        try {
            JsonArray array = JsonParser.parseString(json == null || json.isBlank() ? "[]" : json).getAsJsonArray();
            for (JsonElement element : array) {
                var object = element.getAsJsonObject();
                String cardId = object.has("cardId") ? object.get("cardId").getAsString() : "";
                int quantity = object.has("quantity") ? object.get("quantity").getAsInt() : 1;
                items.add(new ReservationService.Item(cardId, quantity));
            }
        } catch (RuntimeException e) {
            throw new StockOperationException(I18n.t("res.err.parse"));
        }
        return items;
    }
}
