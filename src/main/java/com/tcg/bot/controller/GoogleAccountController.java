package com.tcg.bot.controller;

import com.tcg.bot.service.GoogleAccountService;
import com.tcg.bot.service.GoogleAccountService.GoogleLoginException;
import com.tcg.bot.service.GoogleSheetsService;
import com.tcg.bot.service.StoreSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Conectar la cuenta de Google de la tienda y crear su Sheet. */
@Controller
public class GoogleAccountController {

    private static final Logger log = LoggerFactory.getLogger(GoogleAccountController.class);

    private final GoogleAccountService googleAccountService;
    private final GoogleSheetsService googleSheetsService;
    private final StoreSettingsService storeSettingsService;

    public GoogleAccountController(
            GoogleAccountService googleAccountService,
            GoogleSheetsService googleSheetsService,
            StoreSettingsService storeSettingsService
    ) {
        this.googleAccountService = googleAccountService;
        this.googleSheetsService = googleSheetsService;
        this.storeSettingsService = storeSettingsService;
    }

    @GetMapping("/cuenta/google/conectar")
    public String connect(RedirectAttributes redirectAttributes) {
        try {
            String redirectUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path(GoogleAccountService.CALLBACK_PATH)
                    .toUriString();
            return "redirect:" + googleAccountService.authorizationUrl(redirectUri);
        } catch (GoogleLoginException e) {
            redirectAttributes.addFlashAttribute("googleError", e.getMessage());
            return "redirect:/configuracion";
        }
    }

    @GetMapping(GoogleAccountService.CALLBACK_PATH)
    public String callback(
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "error", required = false) String error,
            RedirectAttributes redirectAttributes
    ) {
        try {
            var status = googleAccountService.completeLogin(state, code, error);
            redirectAttributes.addFlashAttribute("googleSuccess", status.email().isBlank()
                    ? "Cuenta de Google conectada."
                    : "Conectaste " + status.email() + ".");
        } catch (GoogleLoginException e) {
            redirectAttributes.addFlashAttribute("googleError", e.getMessage());
        } catch (Exception e) {
            log.warn("No se pudo completar el login con Google.", e);
            redirectAttributes.addFlashAttribute("googleError", "No se pudo conectar con Google: " + e.getMessage());
        }
        return "redirect:/configuracion";
    }

    @PostMapping("/cuenta/google/crear-sheet")
    public String createSheet(RedirectAttributes redirectAttributes) {
        try {
            String title = storeSettingsService.getStoreName() == null || storeSettingsService.getStoreName().isBlank()
                    ? "Inventario TCG"
                    : "Inventario " + storeSettingsService.getStoreName().trim();
            String spreadsheetId = googleSheetsService.createSpreadsheet(title);
            storeSettingsService.useSpreadsheet(spreadsheetId);
            redirectAttributes.addFlashAttribute("googleSuccess",
                    "Listo: creamos \"" + title + "\" en tu Drive y la app ya lo usa.");
        } catch (Exception e) {
            log.warn("No se pudo crear el Sheet.", e);
            redirectAttributes.addFlashAttribute("googleError", "No se pudo crear el Sheet: " + e.getMessage());
        }
        return "redirect:/configuracion";
    }

    @PostMapping("/cuenta/google/desconectar")
    public String disconnect(RedirectAttributes redirectAttributes) {
        try {
            googleAccountService.disconnect();
            redirectAttributes.addFlashAttribute("googleSuccess", "Desconectaste la cuenta de Google.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("googleError", "No se pudo desconectar: " + e.getMessage());
        }
        return "redirect:/configuracion";
    }
}
