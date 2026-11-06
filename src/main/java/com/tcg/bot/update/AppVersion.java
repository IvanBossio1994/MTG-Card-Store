package com.tcg.bot.update;

import java.util.ArrayList;
import java.util.List;

/**
 * Numero de version tipo "1.2.3" (acepta "v1.2.3" como en los tags de GitHub).
 * Las versiones que no son numeros (por ejemplo "dev" en una compilacion de prueba) no son validas.
 */
public record AppVersion(List<Integer> parts) implements Comparable<AppVersion> {

    public static AppVersion parse(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.startsWith("v") || value.startsWith("V")) {
            value = value.substring(1);
        }
        int dash = value.indexOf('-');
        if (dash >= 0) {
            value = value.substring(0, dash);
        }
        if (value.isEmpty()) {
            return null;
        }
        List<Integer> parts = new ArrayList<>();
        for (String piece : value.split("\\.")) {
            if (piece.isEmpty() || !piece.chars().allMatch(Character::isDigit) || piece.length() > 6) {
                return null;
            }
            parts.add(Integer.parseInt(piece));
        }
        return new AppVersion(List.copyOf(parts));
    }

    public boolean isNewerThan(AppVersion other) {
        return other == null || compareTo(other) > 0;
    }

    @Override
    public int compareTo(AppVersion other) {
        int size = Math.max(parts.size(), other.parts.size());
        for (int i = 0; i < size; i++) {
            int mine = i < parts.size() ? parts.get(i) : 0;
            int theirs = i < other.parts.size() ? other.parts.get(i) : 0;
            if (mine != theirs) {
                return Integer.compare(mine, theirs);
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                text.append('.');
            }
            text.append(parts.get(i));
        }
        return text.toString();
    }
}
