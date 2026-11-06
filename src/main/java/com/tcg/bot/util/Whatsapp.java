package com.tcg.bot.util;

/** Links de WhatsApp con el mensaje ya escrito, para avisar a un cliente con un toque. */
public final class Whatsapp {

    private Whatsapp() {
    }

    /**
     * Si el contacto parece un teléfono abre ese chat (se asume Argentina cuando no tiene código de país);
     * si no, WhatsApp deja elegir a quién mandarlo.
     */
    public static String link(String contact, String text) {
        String phone = number(contact);
        String encoded = java.net.URLEncoder.encode(text, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return "https://wa.me/" + (phone == null ? "" : phone) + "?text=" + encoded;
    }

    public static String number(String contact) {
        if (contact == null || contact.isBlank() || contact.matches(".*[A-Za-z@].*")) {
            return null;
        }
        String digits = contact.replaceAll("\\D", "");
        if (digits.length() < 8 || digits.length() > 15) {
            return null;
        }
        if (contact.trim().startsWith("+") || digits.startsWith("54")) {
            return digits;
        }
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        // Celulares argentinos: 11 15 1234-5678 -> 54 9 11 1234-5678
        if (digits.length() == 12 && digits.substring(2, 4).equals("15")) {
            digits = digits.substring(0, 2) + digits.substring(4);
        }
        return "549" + digits;
    }
}
