package com.tcg.bot.model;

/**
 * Un movimiento de crédito de tienda (hoja "Credito"): positivo cuando el cliente gana saldo
 * (por ejemplo, nos vende cartas), negativo cuando lo usa.
 */
public record CreditEntry(
        String date,
        String time,
        String customer,
        String contact,
        double amount,
        String reason,
        String reference
) {
    public String formattedAmount() {
        String value = new java.text.DecimalFormat("#,##0",
                java.text.DecimalFormatSymbols.getInstance(new java.util.Locale("es", "AR"))).format(Math.round(Math.abs(amount)));
        return (amount < 0 ? "-$ " : "+$ ") + value;
    }

    /** Clave para sumar el saldo: sin mayúsculas ni espacios de más. */
    public String customerKey() {
        return key(customer);
    }

    public static String key(String customer) {
        return customer == null ? "" : customer.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
    }
}
