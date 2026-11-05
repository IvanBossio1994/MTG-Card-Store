package com.tcg.bot.controller;

import com.tcg.bot.service.ManualPriceService;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import com.tcg.bot.util.Amounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** "Poner precio" a una carta sin precio, desde el carrito o la búsqueda. */
@RestController
public class ManualPriceController {

    private static final Logger log = LoggerFactory.getLogger(ManualPriceController.class);

    private final ManualPriceService manualPriceService;

    public ManualPriceController(ManualPriceService manualPriceService) {
        this.manualPriceService = manualPriceService;
    }

    public record PriceResponse(boolean success, String message, String cardId, long price) {
    }

    @PostMapping("/api/cartas/precio")
    public ResponseEntity<PriceResponse> setPrice(@RequestParam(required = false) String cardId,
                                                  @RequestParam(required = false, defaultValue = "0") int row,
                                                  @RequestParam String price) {
        try {
            var card = manualPriceService.setPrice(cardId, row, price);
            long saved = Math.round(Amounts.parse(card.getLocalPrice()));
            return ResponseEntity.ok(new PriceResponse(true,
                    "Precio de " + card.getName() + " guardado en el Sheet.", card.getId(), saved));
        } catch (StockOperationException e) {
            return ResponseEntity.badRequest().body(new PriceResponse(false, e.getMessage(), cardId, 0));
        } catch (Exception e) {
            log.warn("No se pudo guardar el precio manual.", e);
            return ResponseEntity.internalServerError()
                    .body(new PriceResponse(false, "No se pudo guardar el precio: " + e.getMessage(), cardId, 0));
        }
    }
}
