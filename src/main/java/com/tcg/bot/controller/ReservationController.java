package com.tcg.bot.controller;

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
        model.addAttribute("storeName", storeSettingsService.getStoreName());
        model.addAttribute("hasStoreLogo", storeSettingsService.hasLogo());
        model.addAttribute("defaultDays", ReservationService.DEFAULT_DAYS);

        try {
            int expired = reservationService.releaseExpired();
            if (expired > 0 && !model.containsAttribute("reservationMessage")) {
                model.addAttribute("reservationMessage", expired == 1
                        ? "Se venció 1 reserva y sus cartas volvieron al stock."
                        : "Se vencieron " + expired + " reservas y sus cartas volvieron al stock.");
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
                model.addAttribute("reservationError", "No se pudieron cargar las reservas. Revisá la conexión con el Sheet en Configuración.");
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
                    "Reserva " + reservationId + " creada para " + customer.trim() + ". Las cartas ya no figuran como disponibles.");
            redirectAttributes.addFlashAttribute("clearOrder", true);
        } catch (StockOperationException e) {
            redirectAttributes.addFlashAttribute("reservationError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo crear la reserva.", e);
            redirectAttributes.addFlashAttribute("reservationError", "No se pudo crear la reserva: " + e.getMessage());
        }

        return "redirect:/reservas";
    }

    @PostMapping("/reservas/{id}/retirar")
    public String pickUp(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            var order = reservationService.pickUp(id);
            redirectAttributes.addFlashAttribute("reservationMessage",
                    order.customer() + " retiró su pedido. Se registraron $ " + order.formattedTotal() + " en la caja.");
        } catch (StockOperationException e) {
            redirectAttributes.addFlashAttribute("reservationError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo registrar el retiro de {}.", id, e);
            redirectAttributes.addFlashAttribute("reservationError", "No se pudo registrar el retiro: " + e.getMessage());
        }

        return "redirect:/reservas";
    }

    @PostMapping("/reservas/{id}/cancelar")
    public String cancel(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            var order = reservationService.cancel(id);
            redirectAttributes.addFlashAttribute("reservationMessage",
                    "Reserva de " + order.customer() + " cancelada. Las cartas volvieron al stock.");
        } catch (StockOperationException e) {
            redirectAttributes.addFlashAttribute("reservationError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo cancelar la reserva {}.", id, e);
            redirectAttributes.addFlashAttribute("reservationError", "No se pudo cancelar la reserva: " + e.getMessage());
        }

        return "redirect:/reservas";
    }

    /**
     * Link de WhatsApp con el aviso ya escrito. Si el contacto parece un teléfono abre ese chat
     * (se asume Argentina cuando no tiene código de país); si no, WhatsApp deja elegir a quién mandarlo.
     */
    static String whatsappLink(ReservationService.Order order, String storeName) {
        StringBuilder text = new StringBuilder("¡Hola ").append(order.customer()).append("! ");
        if (storeName != null && !storeName.isBlank()) {
            text.append("Te escribimos de ").append(storeName.trim()).append(". ");
        }
        text.append("Tu reserva te espera hasta el ").append(order.formattedExpiresOn()).append(":\n");
        for (var line : order.lines()) {
            text.append("• ").append(line.getQuantity()).append(" × ").append(line.getName()).append("\n");
        }
        text.append("Total: $ ").append(order.formattedTotal()).append(". ¡Te esperamos!");

        String phone = whatsappNumber(order.contact());
        String encoded = java.net.URLEncoder.encode(text.toString(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "https://wa.me/" + (phone == null ? "" : phone) + "?text=" + encoded;
    }

    static String whatsappNumber(String contact) {
        if (contact == null || contact.isBlank() || contact.matches(".*[A-Za-z@].*")) {
            return null;
        }
        String digits = contact.replaceAll("\\D", "");
        if (digits.length() < 8 || digits.length() > 15) {
            return null;
        }
        if (contact.trim().startsWith("+") || digits.startsWith("54")) {
            return digits;
        }
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        // Celulares argentinos: 11 15 1234-5678 -> 54 9 11 1234-5678
        if (digits.length() == 12 && digits.substring(2, 4).equals("15")) {
            digits = digits.substring(0, 2) + digits.substring(4);
        }
        return "549" + digits;
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
            throw new StockOperationException("No se pudo leer el pedido. Volvé a armarlo desde la búsqueda.");
        }
        return items;
    }
}
