package com.tcg.bot.controller;

import com.tcg.bot.service.WhatsappNotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class WhatsappNotificationController {

    private final WhatsappNotificationService notificationService;

    public WhatsappNotificationController(WhatsappNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/api/whatsapp/notificaciones")
    public List<WhatsappNotificationService.WhatsappNotification> notifications(
            @RequestParam(name = "after", required = false, defaultValue = "0") long after
    ) {
        return notificationService.after(after);
    }
}
