package com.vein.news.provider.telegram;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.vein.news.provider.NewsItemDto;

import lombok.extern.slf4j.Slf4j;

/**
 * REAL Telegram (coinness) news bridge to the Python sidecar over the fixed HTTP
 * contract:
 * <ul>
 *   <li>{@code GET /news/telegram?limit=N} ->
 *       {@code {"data":[{"id":..,"text":..,"url":..,"published_at":"..Z"}, ..]}}
 *       (newest first, UTC; on failure {@code {"data":[],"error":".."}}).</li>
 * </ul>
 *
 * <p>The sidecar is optional infrastructure: ANY failure (down, timeout, empty,
 * malformed) is logged at WARN and surfaced as an empty list so the
 * {@link TelegramProvider} can fall back to its deterministic seeds. Sidecar
 * calls must NEVER crash the app.
 */
@Component
@Slf4j
public class NewsSidecarClient {

    private final RestClient restClient;
    private final String baseUrl;

    public NewsSidecarClient(
            @Value("${vein.sidecar.base-url:http://localhost:8099}") String baseUrl) {
        this.baseUrl = baseUrl;
        // Telegram fetch is a cheap scrape: keep both timeouts short.
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        rf.setReadTimeout((int) Duration.ofSeconds(5).toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(rf)
                .defaultHeader("accept", "application/json")
                .defaultHeader("User-Agent", "VEIN/1.0")
                .build();
    }

    /**
     * Fetch the latest coinness Telegram posts (newest first). Returns an empty
     * list on ANY failure (down/timeout/empty/malformed) so the caller can fall
     * back to its seeds. Items are normalized to {@link NewsItemDto} with a null
     * title (body-only posts) and the canonical t.me url.
     */
    public List<NewsItemDto> fetchTelegramNews(int limit) {
        try {
            int want = Math.min(Math.max(limit, 1), 100);
            JsonNode body = restClient.get()
                    .uri(b -> b.path("/news/telegram").queryParam("limit", want).build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = body == null ? null : body.path("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                log.warn("news sidecar telegram ({}) returned no data", baseUrl);
                return List.of();
            }
            List<NewsItemDto> out = new ArrayList<>(data.size());
            for (JsonNode n : data) {
                String text = text(n, "text");
                String url = text(n, "url");
                Instant publishedAt = parseInstant(text(n, "published_at"));
                if (text == null || url == null) {
                    continue;
                }
                out.add(new NewsItemDto(TelegramProvider.SOURCE, null, text, url, publishedAt));
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("news sidecar telegram limit={} failed: {}", limit, e.getMessage());
            return List.of();
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText(null);
        return (s == null || s.isBlank()) ? null : s;
    }

    private static Instant parseInstant(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
