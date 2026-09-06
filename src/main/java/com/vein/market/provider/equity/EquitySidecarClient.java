package com.vein.market.provider.equity;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.vein.common.Timeframe;
import com.vein.market.provider.InstrumentInfo;
import com.vein.market.provider.RawCandle;

import lombok.extern.slf4j.Slf4j;

/**
 * REAL equity market-data bridge to the Python sidecar (yfinance for US,
 * pykrx for KR) over the fixed HTTP contract:
 * <ul>
 *   <li>{@code GET /health} -> {@code {"status":"ok"}}</li>
 *   <li>{@code GET /equity/instruments?market=US|KOSPI|KOSDAQ}</li>
 *   <li>{@code GET /equity/candles?market=..&symbol=..&timeframe=1d|3d|1w&count=..}</li>
 *   <li>{@code GET /equity/index?key=NASDAQ|KOSPI|KOSDAQ}</li>
 *   <li>{@code GET /equity/index/history?key=NASDAQ|KOSPI|KOSDAQ&days=30}</li>
 * </ul>
 *
 * <p>The sidecar is optional infrastructure: ANY failure (down, timeout, empty,
 * malformed) is logged at WARN and surfaced as an empty/absent result so callers
 * fall back to the existing synthetic stub. Sidecar calls must NEVER crash the app.
 */
@Component
@Slf4j
public class EquitySidecarClient {

    public record IndexHistoryPoint(String t, BigDecimal value) {
    }

    private final RestClient restClient;
    private final String baseUrl;

    public EquitySidecarClient(
            @Value("${vein.sidecar.base-url:http://localhost:8099}") String baseUrl) {
        this.baseUrl = baseUrl;
        // yfinance/pykrx can be slow: connect short, read generous.
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        rf.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(rf)
                .defaultHeader("accept", "application/json")
                .defaultHeader("User-Agent", "VEIN/1.0")
                .build();
    }

    /** True if the sidecar reports {@code {"status":"ok"}}; false on any error. */
    public boolean isHealthy() {
        try {
            JsonNode body = restClient.get().uri("/health").retrieve().body(JsonNode.class);
            return body != null && body.path("status").asText("").equalsIgnoreCase("ok");
        } catch (RuntimeException e) {
            log.warn("equity sidecar health check failed ({}): {}", baseUrl, e.getMessage());
            return false;
        }
    }

    /**
     * List instruments for a market. Returns an empty list on any failure
     * (callers should keep their existing instrument source as authoritative).
     */
    public List<InstrumentInfo> listInstruments(String market) {
        try {
            JsonNode body = restClient.get()
                    .uri(b -> b.path("/equity/instruments").queryParam("market", market).build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = body == null ? null : body.path("data");
            if (data == null || !data.isArray()) {
                return List.of();
            }
            List<InstrumentInfo> out = new ArrayList<>(data.size());
            for (JsonNode n : data) {
                String symbol = text(n, "symbol");
                if (symbol == null) {
                    continue;
                }
                String name = text(n, "name");
                out.add(new InstrumentInfo(market, market, symbol, name, name, null));
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("equity sidecar listInstruments market={} failed: {}", market, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetch ascending UTC OHLCV candles for a symbol. Returns an empty list on
     * ANY failure (down/timeout/empty/malformed) so callers fall back to the stub.
     * All candles are marked final (sidecar returns settled daily/weekly bars).
     */
    public List<RawCandle> fetchCandles(String market, String symbol, Timeframe tf, int count) {
        try {
            int want = Math.min(Math.max(count, 1), 1000);
            JsonNode body = restClient.get()
                    .uri(b -> b.path("/equity/candles")
                            .queryParam("market", market)
                            .queryParam("symbol", symbol)
                            .queryParam("timeframe", tf.code())
                            .queryParam("count", want)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = body == null ? null : body.path("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                log.warn("equity sidecar candles market={} symbol={} tf={} returned no data",
                        market, symbol, tf.code());
                return List.of();
            }
            List<RawCandle> out = new ArrayList<>(data.size());
            for (JsonNode n : data) {
                Instant openTime = parseInstant(text(n, "open_time"));
                BigDecimal close = dec(n, "close");
                if (openTime == null || close == null) {
                    continue;
                }
                out.add(new RawCandle(
                        openTime,
                        dec(n, "open"), dec(n, "high"), dec(n, "low"), close,
                        dec(n, "volume"),
                        true));
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("equity sidecar candles market={} symbol={} tf={} failed: {}",
                    market, symbol, tf.code(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetch a market index level ({@code NASDAQ|KOSPI|KOSDAQ}). Returns null on
     * any failure or when the sidecar reports a null value.
     */
    public BigDecimal fetchIndex(String key) {
        try {
            JsonNode body = restClient.get()
                    .uri(b -> b.path("/equity/index").queryParam("key", key).build())
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                return null;
            }
            JsonNode v = body.path("value");
            if (v.isMissingNode() || v.isNull() || !v.isNumber()) {
                return null;
            }
            return v.decimalValue();
        } catch (RuntimeException e) {
            log.warn("equity sidecar index key={} failed: {}", key, e.getMessage());
            return null;
        }
    }

    /** Fetch daily closes for a market index, ascending by time. */
    public List<IndexHistoryPoint> fetchIndexHistory(String key, int days) {
        try {
            JsonNode body = restClient.get()
                    .uri(b -> b.path("/equity/index/history")
                            .queryParam("key", key)
                            .queryParam("days", Math.max(1, Math.min(days, 365)))
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null || !body.path("data").isArray()) {
                return List.of();
            }
            List<IndexHistoryPoint> out = new ArrayList<>();
            for (JsonNode row : body.path("data")) {
                String t = text(row, "t");
                BigDecimal value = dec(row, "value");
                if (t != null && value != null) {
                    out.add(new IndexHistoryPoint(t, value));
                }
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("equity sidecar index history key={} failed: {}", key, e.getMessage());
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

    private static BigDecimal dec(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isNumber()) {
            return null;
        }
        return v.decimalValue();
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
