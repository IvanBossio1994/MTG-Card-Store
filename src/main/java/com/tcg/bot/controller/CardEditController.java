package com.tcg.bot.controller;

import com.tcg.bot.i18n.I18n;
import com.tcg.bot.service.CardDetailsService;
import com.tcg.bot.service.ManualPriceService;
import com.tcg.bot.service.StockOperationsService.StockOperationException;
import com.tcg.bot.util.Amounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cambios chicos a una carta desde la búsqueda o el carrito: precio a mano, costo y ubicación. */
@RestController
public class CardEditController {

    private static final Logger log = LoggerFactory.getLogger(CardEditController.class);

    private final ManualPriceService manualPriceService;
    private final CardDetailsService cardDetailsService;

    public CardEditController(ManualPriceService manualPriceService, CardDetailsService cardDetailsService) {
        this.manualPriceService = manualPriceService;
        this.cardDetailsService = cardDetailsService;
    }

    public record DetailsResponse(boolean success, String message, String cardId, String cost, String location) {
    }

    @PostMapping("/api/cartas/detalle")
    public ResponseEntity<DetailsResponse> setDetails(@RequestParam(required = false) String cardId,
                                                      @RequestParam(required = false, defaultValue = "0") int row,
                                                      @RequestParam(required = false, defaultValue = "") String cost,
                                                      @RequestParam(required = false, defaultValue = "") String location) {
        try {
            var card = cardDetailsService.save(cardId, row, cost, location);
            Double saved = card.costValue();
            return ResponseEntity.ok(new DetailsResponse(true, I18n.t("details.saved", card.getName()), card.getId(),
                    saved == null ? "" : com.tcg.bot.i18n.Money.number(saved), card.getLocation()));
        } catch (StockOperationException e) {
            return ResponseEntity.badRequest().body(new DetailsResponse(false, e.getMessage(), cardId, "", ""));
        } catch (Exception e) {
            log.warn("No se pudieron guardar costo y ubicación.", e);
            return ResponseEntity.internalServerError()
                    .body(new DetailsResponse(false, I18n.t("details.err.save", e.getMessage()), cardId, "", ""));
        }
    }

    public record PriceResponse(boolean success, String message, String cardId, double price) {
    }

    @PostMapping("/api/cartas/precio")
    public ResponseEntity<PriceResponse> setPrice(@RequestParam(required = false) String cardId,
                                                  @RequestParam(required = false, defaultValue = "0") int row,
                                                  @RequestParam String price) {
        try {
            var card = manualPriceService.setPrice(cardId, row, price);
            double saved = com.tcg.bot.i18n.Money.round(Amounts.parse(card.getLocalPrice()));
            return ResponseEntity.ok(new PriceResponse(true,
                    I18n.t("price.saved", card.getName()), card.getId(), saved));
        } catch (StockOperationException e) {
            return ResponseEntity.badRequest().body(new PriceResponse(false, e.getMessage(), cardId, 0));
        } catch (Exception e) {
            log.warn("No se pudo guardar el precio manual.", e);
            return ResponseEntity.internalServerError()
                    .body(new PriceResponse(false, I18n.t("price.err.save", e.getMessage()), cardId, 0));
        }
    }
}
