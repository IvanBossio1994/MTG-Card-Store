package com.tcg.bot.model;

import com.tcg.bot.util.Amounts;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Cartas que un cliente deja para que la tienda las venda (pestaña "Consignacion"). Cada entrega es una fila del
 * inventario propia (con su ID), así las ventas de esa fila son del dueño. Al dueño le toca {@code percent} % de lo
 * que se cobró; {@code paid} es lo que ya se le pagó.
 */
@Getter
@Setter
public class Consignment {

    public static final List<String> HEADER = List.of(
            "Fecha", "Dueño", "Contacto", "ID carta", "Carta", "Edicion", "Cantidad", "% para el dueño", "Pagado",
            "Estado", "ID");

    /** Está a la venta (o se vendió y falta pagarle). */
    public static final String STATUS_ACTIVE = "ACTIVA";
    /** Se le devolvió lo que no se vendió. */
    public static final String STATUS_RETURNED = "DEVUELTA";

    private int rowIndex;
    private String date;
    private String owner;
    private String contact;
    private String cardId;
    private String name;
    private String edition;
    private int quantity;
    private double percent;
    private double paid;
    private String status;
    private String id;

    public static Consignment fromRow(List<String> row, int rowIndex) {
        Consignment item = new Consignment();
        item.rowIndex = rowIndex;
        item.date = row.get(0);
        item.owner = row.get(1);
        item.contact = row.get(2);
        item.cardId = row.get(3).trim();
        item.name = row.get(4);
        item.edition = row.get(5);
        item.quantity = Math.max(Amounts.parseInt(row.get(6)), 0);
        item.percent = Math.max(0, Math.min(100, Amounts.parse(row.get(7))));
        item.paid = Math.max(0, Amounts.parse(row.get(8)));
        item.status = row.get(9).isBlank() ? STATUS_ACTIVE : row.get(9).trim().toUpperCase();
        item.id = row.get(10);
        return item;
    }

    public List<Object> toRow() {
        List<Object> row = new ArrayList<>();
        row.add(safe(date));
        row.add(safe(owner));
        row.add(safe(contact));
        row.add(safe(cardId));
        row.add(safe(name));
        row.add(safe(edition));
        row.add(quantity);
        row.add(com.tcg.bot.i18n.Money.plainNumber(percent));
        row.add(com.tcg.bot.i18n.Money.sheetValue(paid));
        row.add(safe(status));
        row.add(safe(id));
        return row;
    }

    public boolean isReturned() {
        return STATUS_RETURNED.equalsIgnoreCase(status);
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
