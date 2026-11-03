package com.tcg.bot.controller;

import com.tcg.bot.service.GoogleAccountService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Deja el estado de la cuenta de Google disponible en todas las pantallas. */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
public class GoogleAccountModelAdvice {

    private final GoogleAccountService googleAccountService;

    public GoogleAccountModelAdvice(GoogleAccountService googleAccountService) {
        this.googleAccountService = googleAccountService;
    }

    @ModelAttribute("googleAccount")
    public GoogleAccountService.Status googleAccount() {
        return googleAccountService.status();
    }
}
