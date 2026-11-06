package com.tcg.bot.controller;

import com.tcg.bot.update.UpdateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Actualizaciones de la app: estado, buscar ahora e instalar. */
@RestController
public class UpdateController {

    private final UpdateService updateService;

    public UpdateController(UpdateService updateService) {
        this.updateService = updateService;
    }

    @GetMapping("/api/actualizacion")
    public UpdateService.Status status() {
        return updateService.status();
    }

    @PostMapping("/api/actualizacion/buscar")
    public UpdateService.Status check() {
        updateService.checkInBackground();
        return updateService.status();
    }

    @PostMapping("/api/actualizacion/instalar")
    public ResponseEntity<UpdateService.Status> install() {
        boolean started = updateService.installNow();
        return started
                ? ResponseEntity.ok(updateService.status())
                : ResponseEntity.badRequest().body(updateService.status());
    }
}
