package com.tcg.bot.service.games;

import com.tcg.bot.model.Game;
import com.tcg.bot.model.GamePrint;

import java.util.List;

/** Fuente gratis de cartas y precios de un juego. */
public interface GameCatalog {

    Game game();

    /** Impresiones que coinciden con el nombre (o parte), de la más nueva a la más vieja. */
    List<GamePrint> search(String name) throws Exception;

    /** Se separa para probar sin internet: recibe la URL y devuelve el JSON, o null si no hay resultados. */
    interface Fetcher {
        String get(String url) throws Exception;
    }

    static Fetcher httpFetcher(String apiKeyHeader, String apiKey) {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10)).build();
        return url -> {
            var builder = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(25))
                    .header("Accept", "application/json")
                    .header("User-Agent", "MTGCardStore/2.0")
                    .GET();
            if (apiKeyHeader != null && apiKey != null && !apiKey.isBlank()) {
                builder.header(apiKeyHeader, apiKey);
            }
            var response = client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404 || response.statusCode() == 400) {
                return null;
            }
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("El catálogo respondió " + response.statusCode());
            }
            return response.body();
        };
    }
}
