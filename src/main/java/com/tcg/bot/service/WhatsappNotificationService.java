package com.tcg.bot.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WhatsappNotificationService {

    private static final ZoneId APP_ZONE = ZoneId.of("America/Buenos_Aires");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_EVENTS = 50;

    private final AtomicLong sequence = new AtomicLong();
    private final CopyOnWriteArrayList<WhatsappNotification> notifications = new CopyOnWriteArrayList<>();

    public void orderCreated(String client, String phone, int lines) {
        long id = sequence.incrementAndGet();
        notifications.add(new WhatsappNotification(
                id,
                LocalDateTime.now(APP_ZONE).format(FORMATTER),
                "Nuevo pedido por WhatsApp: " + client + " (" + phone + "), " + lines + " carta(s)."
        ));

        if (notifications.size() > MAX_EVENTS) {
            notifications.remove(0);
        }
    }

    public List<WhatsappNotification> after(long lastSeenId) {
        return notifications.stream()
                .filter(notification -> notification.id() > lastSeenId)
                .sorted(Comparator.comparingLong(WhatsappNotification::id))
                .toList();
    }

    public List<WhatsappNotification> all() {
        return new ArrayList<>(notifications);
    }

    public record WhatsappNotification(long id, String createdAt, String message) {
    }
}
