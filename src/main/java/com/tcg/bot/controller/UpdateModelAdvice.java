package com.tcg.bot.controller;

import com.tcg.bot.update.UpdateService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Deja el estado de las actualizaciones disponible en todas las pantallas (aviso de version nueva). */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
public class UpdateModelAdvice {

    private final UpdateService updateService;

    public UpdateModelAdvice(UpdateService updateService) {
        this.updateService = updateService;
    }

    @ModelAttribute("appUpdate")
    public UpdateService.Status appUpdate() {
        return updateService.status();
    }
}
