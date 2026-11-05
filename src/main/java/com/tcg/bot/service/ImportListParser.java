package com.tcg.bot.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lee las líneas de "Importar lista" (formato tipo "4 Sol Ring (CMM) 410 foil") y junta las repetidas.
 * Solo texto: no consulta Card Kingdom ni el Sheet.
 */
public final class ImportListParser {

    public static final Pattern LEADING_QUANTITY_PATTERN =
            Pattern.compile("^\\s*(\\d+)\\s*x?\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    public static final Pattern TRAILING_QUANTITY_PATTERN =
            Pattern.compile("^\\s*(.+?)\\s+x\\s*(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);
    public static final Pattern SET_CODE_PATTERN =
            Pattern.compile("[\\[(]([A-Za-z0-9]{2,8})[\\])]");
    public static final Pattern COLLECTOR_PATTERN =
            Pattern.compile("(?:#|\\s)([A-Za-z0-9]+(?:-[A-Za-z0-9]+)?)\\s*$");

    private ImportListParser() {
    }

    public record ParsedImportLine(
            String originalLine,
            int quantity,
            String name,
            String setCode,
            String collectorNumber,
            Boolean foil,
            int duplicateCount
    ) {
    }

    public static List<ParsedImportLine> parseAndMergeImportLines(String rawList) {
        Map<String, ParsedImportLine> linesByKey = new LinkedHashMap<>();

        for (String line : rawList.split("\\R")) {
            ParsedImportLine parsedLine = parseImportLine(line);

            if (parsedLine == null) {
                continue;
            }

            String key = importLineKey(parsedLine);
            ParsedImportLine existingLine = linesByKey.get(key);

            if (existingLine == null) {
                linesByKey.put(key, parsedLine);
                continue;
            }

            linesByKey.put(key, new ParsedImportLine(
                    existingLine.originalLine(),
                    existingLine.quantity() + parsedLine.quantity(),
                    existingLine.name(),
                    existingLine.setCode(),
                    existingLine.collectorNumber(),
                    existingLine.foil(),
                    existingLine.duplicateCount() + 1
            ));
        }

        return new ArrayList<>(linesByKey.values());
    }

    public static String importLineKey(ParsedImportLine line) {
        return normalizeImportedName(line.name())
                + "|"
                + line.setCode().toLowerCase()
                + "|"
                + line.collectorNumber()
                + "|"
                + (line.foil() == null ? "" : line.foil());
    }

    public static List<String> importNameCandidates(String name) {
        String normalizedName = normalizeImportedName(name);
        List<String> candidates = new ArrayList<>();

        if (!normalizedName.isBlank()) {
            candidates.add(normalizedName);
        }

        if (name != null && name.contains("/")) {
            for (String faceName : name.split("/")) {
                String normalizedFace = normalizeImportedName(faceName);

                if (!normalizedFace.isBlank() && !candidates.contains(normalizedFace)) {
                    candidates.add(normalizedFace);
                }
            }
        }

        return candidates;
    }

    public static ParsedImportLine parseImportLine(String line) {
        if (line == null) {
            return null;
        }

        String normalizedLine = line.trim();
        if (normalizedLine.isBlank()
                || normalizedLine.startsWith("//")
                || normalizedLine.startsWith("#")) {
            return null;
        }

        int quantity = 1;
        String namePart = normalizedLine;

        Matcher leadingQuantity = LEADING_QUANTITY_PATTERN.matcher(namePart);
        Matcher trailingQuantity = TRAILING_QUANTITY_PATTERN.matcher(namePart);

        if (leadingQuantity.matches()) {
            quantity = Integer.parseInt(leadingQuantity.group(1));
            namePart = leadingQuantity.group(2).trim();
        } else if (trailingQuantity.matches()) {
            namePart = trailingQuantity.group(1).trim();
            quantity = Integer.parseInt(trailingQuantity.group(2));
        }

        String setCode = "";
        Matcher setCodeMatcher = SET_CODE_PATTERN.matcher(namePart);
        if (setCodeMatcher.find()) {
            setCode = setCodeMatcher.group(1);
            namePart = setCodeMatcher.replaceAll("").trim();
        }

        String collector = "";
        Matcher collectorMatcher = COLLECTOR_PATTERN.matcher(namePart);
        if (collectorMatcher.find() && isCollectorToken(namePart, collectorMatcher)) {
            collector = collectorMatcher.group(1);
            namePart = namePart.substring(0, collectorMatcher.start()).trim();
        }

        String lowercaseNamePart = namePart.toLowerCase();
        Boolean foil = null;
        if (lowercaseNamePart.contains("nonfoil") || lowercaseNamePart.contains("non foil")) {
            foil = false;
        } else if (lowercaseNamePart.contains("foil")) {
            foil = true;
        }
        namePart = namePart
                .replaceAll("(?i)\\bnon[- ]?foil\\b", "")
                .replaceAll("(?i)\\bfoil\\b", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (namePart.isBlank() || quantity <= 0) {
            return null;
        }

        return new ParsedImportLine(
                normalizedLine,
                quantity,
                namePart,
                setCode,
                normalizeImportCollector(collector),
                foil,
                0
        );
    }

    public static boolean isCollectorToken(String line, Matcher collectorMatcher) {
        String collector = collectorMatcher.group(1);

        if (collector != null && collector.matches(".*\\d.*")) {
            return true;
        }

        int tokenStart = collectorMatcher.start(1);
        return tokenStart > 0 && line.charAt(tokenStart - 1) == '#';
    }

    public static String importCollectorFromSku(String sku) {
        if (sku == null) {
            return "";
        }

        String collector = sku.contains("-")
                ? sku.substring(sku.indexOf("-") + 1)
                : sku;

        return normalizeImportCollector(collector);
    }

    public static String normalizeImportCollector(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .replaceFirst("^0+(?!$)", "");
    }

    public static String normalizeImportedName(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase()
                .replace("//", "/")
                .replaceAll("\\s*/\\s*", " / ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
