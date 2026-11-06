package com.tcg.bot.model;

import com.tcg.bot.util.Amounts;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Una carta que un cliente busca y la tienda no tiene (pestaña "Buscadas"). */
@Getter
@Setter
public class WantedCard {

    public static final List<String> HEADER = List.of(
            "Fecha", "Cliente", "Contacto", "Carta", "Codigo de set", "Cantidad", "Estado", "Actualizado", "ID");

    /** Todavía no se le avisó. */
    public static final String STATUS_WANTED = "BUSCANDO";
    /** Ya se le avisó que entró; queda a la vista hasta cerrarla. */
    public static final String STATUS_NOTIFIED = "AVISADO";
    /** Se la llevó, ya no la quiere o se borró. */
    public static final String STATUS_CLOSED = "CERRADA";

    private int rowIndex;
    private String date;
    private String customer;
    private String contact;
    private String name;
    private String setCode;
    private int quantity;
    private String status;
    private String updatedAt;
    private String id;

    public static WantedCard fromRow(List<String> row, int rowIndex) {
        WantedCard card = new WantedCard();
        card.rowIndex = rowIndex;
        card.date = row.get(0);
        card.customer = row.get(1);
        card.contact = row.get(2);
        card.name = row.get(3);
        card.setCode = row.get(4);
        card.quantity = Math.max(Amounts.parseInt(row.get(5)), 1);
        card.status = row.get(6).isBlank() ? STATUS_WANTED : row.get(6).trim().toUpperCase();
        card.updatedAt = row.get(7);
        card.id = row.get(8);
        return card;
    }

    public List<Object> toRow() {
        List<Object> row = new ArrayList<>();
        row.add(safe(date));
        row.add(safe(customer));
        row.add(safe(contact));
        row.add(safe(name));
        row.add(safe(setCode));
        row.add(quantity);
        row.add(safe(status));
        row.add(safe(updatedAt));
        row.add(safe(id));
        return row;
    }

    public boolean isOpen() {
        return !STATUS_CLOSED.equalsIgnoreCase(status);
    }

    public boolean isNotified() {
        return STATUS_NOTIFIED.equalsIgnoreCase(status);
    }

    public String getFormattedDate() {
        try {
            return java.time.LocalDate.parse(date).format(com.tcg.bot.i18n.Dates.shortDate());
        } catch (RuntimeException e) {
            return safe(date);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
