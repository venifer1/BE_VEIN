package com.vein.theme;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Loads the legacy classification maps ({@code theme/theme_map_{coin,us,kr}.json},
 * copied from {@code data/theme_map_*.json}) into memory. Each entry maps an
 * instrument ref (coin symbol / US ticker / KR 6-digit code) to its theme, source
 * and confidence. Missing files degrade gracefully to an empty map (the service
 * falls back to a keyword heuristic).
 */
@Component
@Slf4j
public class ThemeMapLoader {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** One classified entry from a legacy map file. */
    public record Entry(String theme, String source, BigDecimal confidence) {
    }

    /** ref (upper-cased) -> entry for the given market (CRYPTO|US|KR). */
    public Map<String, Entry> load(String market) {
        String file = switch (market) {
            case "CRYPTO" -> "theme/theme_map_coin.json";
            case "US" -> "theme/theme_map_us.json";
            case "KR" -> "theme/theme_map_kr.json";
            default -> null;
        };
        Map<String, Entry> out = new HashMap<>();
        if (file == null) {
            return out;
        }
        ClassPathResource res = new ClassPathResource(file);
        if (!res.exists()) {
            log.debug("theme map {} not present; heuristic fallback only", file);
            return out;
        }
        try (InputStream in = res.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            JsonNode items = root.get("items");
            if (items == null || !items.isObject()) {
                return out;
            }
            items.fields().forEachRemaining(e -> {
                JsonNode v = e.getValue();
                String theme = text(v, "theme");
                if (theme == null) {
                    return;
                }
                String source = text(v, "source");
                BigDecimal conf = v.has("confidence") && v.get("confidence").isNumber()
                        ? v.get("confidence").decimalValue() : null;
                out.put(e.getKey().trim().toUpperCase(),
                        new Entry(theme, source == null ? "manual_json" : source, conf));
            });
        } catch (Exception e) {
            log.warn("failed to load theme map {}: {}", file, e.getMessage());
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }
}
