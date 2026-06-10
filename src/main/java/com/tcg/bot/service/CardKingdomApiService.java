package com.tcg.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tcg.bot.dto.CardKingdomPriceListResponse;
import com.tcg.bot.dto.CardKingdomProduct;
import com.tcg.bot.dto.SearchQuery;
import com.tcg.bot.model.InventoryCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

@Service
public class CardKingdomApiService {

    private static final Logger log = LoggerFactory.getLogger(CardKingdomApiService.class);

    private static final String PRICE_LIST_URL =
            "https://api.cardkingdom.com/api/v2/pricelist";

    private final RestTemplate restTemplate =
            new RestTemplate();

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    // ---- Cache pricelist ----
    private final String cacheFile;

    // ---- Duración cache ----
    private static final long CACHE_HOURS = 1;

    public CardKingdomApiService(
            @Value("${ck.cache-file:config/cache/ck-pricelist.json}") String cacheFile
    ) {
        this.cacheFile = cacheFile;
    }

    /**
     * Obtiene pricelist CK.
     */
    public CardKingdomPriceListResponse getPriceList() {
        return getPriceList(false);
    }

    /**
     * Obtiene pricelist CK, permitiendo forzar una descarga actualizada.
     */
    public CardKingdomPriceListResponse getPriceList(boolean forceRefresh) {

        try {

            Path cachePath =
                    Paths.get(cacheFile);

            // ---- Existe cache ----
            if (!forceRefresh && Files.exists(cachePath)) {

                FileTime lastModified =
                        Files.getLastModifiedTime(
                                cachePath
                        );

                Instant cacheInstant =
                        lastModified.toInstant();

                long hoursOld =
                        Duration.between(
                                cacheInstant,
                                Instant.now()
                        ).toHours();

                // ---- Cache válida ----
                if (hoursOld < CACHE_HOURS) {

                    log.info("Usando cache local CK.");

                    String cachedJson =
                            Files.readString(
                                    cachePath
                            );

                    return objectMapper.readValue(
                            cachedJson,
                            CardKingdomPriceListResponse.class
                    );
                }

                log.info("Cache vencida. Re descargando...");
            }

            // ---- Descargar API ----
            log.info("Descargando pricelist CK...");

            HttpHeaders headers =
                    new HttpHeaders();

            headers.set(
                    HttpHeaders.USER_AGENT,
                    "Mozilla/5.0"
            );

            HttpEntity<Void> entity =
                    new HttpEntity<>(headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            PRICE_LIST_URL,
                            HttpMethod.GET,
                            entity,
                            String.class
                    );

            String json =
                    response.getBody();

            if (cachePath.getParent() != null) {
                Files.createDirectories(cachePath.getParent());
            }

            // ---- Guardar cache ----
            Files.writeString(
                    cachePath,
                    json
            );

            log.info("Cache CK guardada.");

            // ---- Parsear ----
            return objectMapper.readValue(
                    json,
                    CardKingdomPriceListResponse.class
            );

        } catch (Exception e) {

            log.warn("No se pudo obtener la pricelist de Card Kingdom.", e);

            return null;
        }
    }

    /**
     * Busca producto exacto.
     */
    public CardKingdomProduct findProduct(
            InventoryCard card
    ) {
        return findProduct(card, getPriceList());
    }

    /**
     * Busca producto exacto utilizando una lista de precios ya cargada.
     */
    public CardKingdomProduct findProduct(
            InventoryCard card,
            CardKingdomPriceListResponse response
    ) {

        if (card == null
                || card.getName() == null
                || card.getName().isBlank()
                || card.getCollectorNumber() == null
                || card.getCollectorNumber().isBlank()
                || card.getPrinting() == null
                || card.getPrinting().isBlank()) {

            return null;
        }

        boolean hasSetCode =
                card.getSetCode() != null
                        && !card.getSetCode().isBlank();

        boolean hasSetName =
                card.getSetName() != null
                        && !card.getSetName().isBlank();

        if (!hasSetCode && !hasSetName) {
            return null;
        }

        String expectedSkuPrefix =
                (hasSetCode ? card.getSetCode().trim() : "")
                        + "-";

        if (response == null || response.getData() == null) {
            return null;
        }

        CardKingdomProduct exactMatch = null;

        for (var product : response.getData()) {

            if (product.getName() == null
                    || !product.getName().equalsIgnoreCase(card.getName())) {
                continue;
            }

            if (hasSetCode
                    && (product.getSku() == null
                    || !product.getSku().toLowerCase()
                    .startsWith(expectedSkuPrefix.toLowerCase()))) {
                continue;
            }

            if (hasSetName
                    && (product.getEdition() == null
                    || !product.getEdition().equalsIgnoreCase(card.getSetName()))) {
                continue;
            }

            if (!collectorMatches(product.getSku(), card.getCollectorNumber())) {
                continue;
            }

            boolean productFoil =
                    "true".equalsIgnoreCase(product.getFoil());

            if (productFoil != card.isFoil()) {
                continue;
            }

            if (exactMatch != null) {
                log.warn("Coincidencia ambigua para {}. No se actualiza automaticamente.", card.getName());

                return null;
            }

            exactMatch = product;
        }

        return exactMatch;
    }

    /**
     * Busca múltiples coincidencias.
     */
    public java.util.List<CardKingdomProduct> searchProducts(String input) {

        var response = getPriceList();

        if (response == null || response.getData() == null) {
            return java.util.Collections.emptyList();
        }

        java.util.Set<String> validSetCodes = response.getData()
                .stream()
                .map(CardKingdomProduct::getSku)
                .filter(sku -> sku != null && sku.contains("-"))
                .map(sku -> sku.substring(0, sku.indexOf("-")).toLowerCase())
                .collect(java.util.stream.Collectors.toSet());

        java.util.Set<String> validSetNames = response.getData()
                .stream()
                .map(CardKingdomProduct::getEdition)
                .filter(edition -> edition != null && !edition.isBlank())
                .map(edition -> edition.trim().toLowerCase())
                .collect(java.util.stream.Collectors.toSet());

        SearchQuery query = parseSearchQuery(input, validSetCodes, validSetNames);

        return response.getData()
                .stream()
                .filter(product -> {

                    if (product.getName() == null) {
                        return false;
                    }

                    if (!matchesName(product, query.getName())) {
                        return false;
                    }

                    if (query.getSetCode() != null && !query.getSetCode().isBlank()) {

                        String setCodeQuery = query.getSetCode().toLowerCase() + "-";

                        boolean matchesSku = product.getSku() != null
                                && product.getSku().toLowerCase()
                                .startsWith(setCodeQuery);

                        boolean matchesEdition = product.getEdition() != null
                                && normalizeSearchText(product.getEdition())
                                .contains(normalizeSearchText(query.getSetCode()));

                        if (!matchesSku && !matchesEdition) {

                            return false;
                        }
                    }

                    if (query.getSet() != null && !query.getSet().isBlank()) {

                        if (product.getEdition() == null
                                || !product.getEdition().equalsIgnoreCase(query.getSet())) {
                            return false;
                        }
                    }

                    if (query.getCollectorNumber() != null && !query.getCollectorNumber().isBlank()) {

                        String collector = normalizeCollectorNumber(query.getCollectorNumber());

                        boolean matchesSku = product.getSku() != null
                                && collectorMatches(product.getSku(), query.getCollectorNumber());

                        boolean matchesVariation = product.getVariation() != null
                                && normalizeCollectorNumber(product.getVariation()).contains(collector);

                        if (!matchesSku && !matchesVariation) {
                            return false;
                        }
                    }

                    if (query.getFoil() != null) {

                        boolean productFoil = "true".equalsIgnoreCase(product.getFoil());

                        if (productFoil != query.getFoil()) {
                            return false;
                        }
                    }

                    return true;
                })
                .toList();
    }

    public SearchQuery parseSearchQuery(String input) {
        return parseSearchQuery(input, java.util.Set.of(), java.util.Set.of());
    }

    private SearchQuery parseSearchQuery(
            String input,
            java.util.Set<String> validSetCodes,
            java.util.Set<String> validSetNames
    ) {

        SearchQuery query =
                new SearchQuery();

        if (input == null || input.isBlank()) {
            query.setName("");
            return query;
        }

        String[] rawParts = input.split(",");
        java.util.List<String> parts = java.util.Arrays.stream(rawParts)
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();

        int nameEndsAt = parts.size() - 1;

        for (int i = parts.size() - 1; i >= 0; i--) {
            if (applySearchToken(parts.get(i), query, validSetCodes, validSetNames)) {
                nameEndsAt = i - 1;
                continue;
            }

            break;
        }

        if (nameEndsAt >= 0) {
            query.setName(String.join(", ", parts.subList(0, nameEndsAt + 1)));
        } else {
            query.setName("");
        }

        return query;
    }

    private boolean applySearchToken(
            String rawValue,
            SearchQuery query,
            java.util.Set<String> validSetCodes,
            java.util.Set<String> validSetNames
    ) {
        String value = rawValue.trim();
        String normalized = value.toLowerCase();

        if (normalized.equals("foil")) {
            query.setFoil(true);
            return true;
        }

        if (normalized.equals("nonfoil")) {
            query.setFoil(false);
            return true;
        }

        if (normalized.startsWith("set:") || normalized.startsWith("setcode:")) {
            String setCode = value.substring(value.indexOf(":") + 1).trim();
            if (!setCode.isBlank()) {
                query.setSetCode(setCode);
                return true;
            }
        }

        if (normalized.startsWith("edicion:")
                || normalized.startsWith("edición:")
                || normalized.startsWith("edition:")) {
            String setName = value.substring(value.indexOf(":") + 1).trim();
            if (!setName.isBlank()) {
                query.setSet(setName);
                return true;
            }
        }

        if (normalized.startsWith("num:")
                || normalized.startsWith("numero:")
                || normalized.startsWith("número:")
                || normalized.startsWith("collector:")) {
            String collector = value.substring(value.indexOf(":") + 1).trim();
            if (!collector.isBlank()) {
                query.setCollectorNumber(collector);
                return true;
            }
        }

        if (normalized.matches("\\d+")) {
            query.setCollectorNumber(value);
            return true;
        }

        if (query.getSetCode() == null
                && !validSetCodes.isEmpty()
                && validSetCodes.contains(normalized)) {
            query.setSetCode(value);
            return true;
        }

        if (query.getSet() == null
                && !validSetNames.isEmpty()
                && validSetNames.contains(normalized)) {
            query.setSet(value);
            return true;
        }

        return false;
    }

    private boolean collectorMatches(String productSku, String localCollectorNumber) {
        if (productSku == null || localCollectorNumber == null) {
            return false;
        }

        String productCollector = collectorFromSku(productSku);
        String productToken = normalizeCollectorToken(productCollector);
        String localToken = normalizeCollectorToken(localCollectorNumber);

        if (!productToken.isBlank() && productToken.equals(localToken)) {
            return true;
        }

        String productNumeric = normalizeCollectorNumber(productCollector);
        String localNumeric = normalizeCollectorNumber(localCollectorNumber);

        return !productNumeric.isBlank() && productNumeric.equals(localNumeric);
    }

    private boolean matchesName(CardKingdomProduct product, String queryName) {
        String normalizedProductName = normalizeSearchText(product.getName());
        String normalizedVariation = normalizeSearchText(product.getVariation());
        String normalizedQueryName = normalizeSearchText(queryName);

        if (normalizedQueryName.isBlank()
                || normalizedProductName.contains(normalizedQueryName)
                || normalizedVariation.contains(normalizedQueryName)) {
            return true;
        }

        for (String faceName : normalizedNameFaces(queryName)) {
            if (normalizedProductName.contains(faceName)
                    || normalizedVariation.contains(faceName)) {
                return true;
            }
        }

        return false;
    }

    private java.util.List<String> normalizedNameFaces(String value) {
        if (value == null || !value.contains("/")) {
            return java.util.List.of();
        }

        return java.util.Arrays.stream(value.split("/"))
                .map(this::normalizeSearchText)
                .filter(part -> !part.isBlank())
                .toList();
    }

    private String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String collectorFromSku(String sku) {
        if (sku == null || !sku.contains("-")) {
            return sku == null ? "" : sku;
        }

        return sku.substring(sku.indexOf("-") + 1);
    }

    private String normalizeCollectorToken(String value) {
        if (value == null) {
            return "";
        }

        return value
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .replaceFirst("^0+(?!$)", "");
    }

    private String normalizeCollectorNumber(String value) {

        if (value == null) {
            return "";
        }

        return value
                .toLowerCase()
                .replaceAll("[^0-9]", "")
                .replaceFirst("^0+(?!$)", "");
    }
}
