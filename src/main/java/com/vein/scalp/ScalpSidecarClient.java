package com.vein.scalp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.vein.common.TimeUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * REAL scalp (틱띄기) microstructure bridge to the Python sidecar, which derives
 * the data from a live Upbit WebSocket feed over the fixed HTTP contract:
 * <ul>
 *   <li>{@code GET /scalp/ranking?limit=N} ->
 *       {@code {"data":[{symbol,scalp_score,spread_ticks,tps,micro_vol,ob_imbalance,wall_state}, ..]}}</li>
 *   <li>{@code GET /scalp/{symbol}} ->
 *       {@code {"data":{..,recent_trades:[{price,volume,side}],orderbook:[{price,size,side}],cancel_suspicion}}}</li>
 * </ul>
 *
 * <p>Maps the sidecar JSON straight onto the existing {@link ScalpDto} response
 * shapes (API_CONTRACT v2, unchanged) so {@link ScalpService} can PREFER this real
 * WS data and fall back to the {@code RestPollingScalpCollector} approximation only
 * when this returns empty/null. The sidecar is optional infrastructure: ANY failure
 * (down, timeout, empty, malformed) is logged at WARN and surfaced as an
 * empty list / null so the caller falls back. Sidecar calls must NEVER crash the app.
 */
@Component
@Slf4j
public class ScalpSidecarClient {

    private final RestClient restClient;
    private final String baseUrl;

    public ScalpSidecarClient(
            @Value("${vein.sidecar.base-url:http://localhost:8099}") String baseUrl) {
        this.baseUrl = baseUrl;
        // WS-derived data is served from an in-memory cache: keep both timeouts short.
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        rf.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(rf)
                .defaultHeader("accept", "application/json")
                .defaultHeader("User-Agent", "VEIN/1.0")
                .build();
    }

    /**
     * Fetch the real WS-derived ranking (already score-desc on the sidecar). Returns
     * an empty list on ANY failure so the caller falls back to the REST collector.
     */
    public List<ScalpDto.RankingRow> fetchRanking(int limit) {
        try {
            int want = Math.min(Math.max(limit, 1), 100);
            JsonNode body = restClient.get()
                    .uri(b -> b.path("/scalp/ranking").queryParam("limit", want).build())
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode data = body == null ? null : body.path("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                return List.of();
            }
            List<ScalpDto.RankingRow> out = new ArrayList<>(data.size());
            int rank = 1;
            for (JsonNode n : data) {
                String symbol = text(n, "symbol");
                if (symbol == null) {
                    continue;
                }
                out.add(new ScalpDto.RankingRow(
                        rank++, symbol,
                        num(n, "scalp_score"), num(n, "spread_ticks"), num(n, "tps"),
                        num(n, "micro_vol"), num(n, "ob_imbalance"), text(n, "wall_state")));
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("scalp sidecar ranking failed ({}): {}", baseUrl, e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetch the real WS-derived detail for one symbol. Returns null on ANY failure
     * (or empty body) so the caller falls back to the REST collector's stored data.
     */
    public ScalpDto.Detail fetchDetail(String symbol) {
        try {
            JsonNode body = restClient.get()
                    .uri(b -> b.path("/scalp/{symbol}").build(symbol))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode d = body == null ? null : body.path("data");
            if (d == null || d.isMissingNode() || d.isNull() || !d.isObject()) {
                return null;
            }
            String sym = text(d, "symbol");
            if (sym == null) {
                return null;
            }

            // Buy/sell ratio + count from the recent trade flow.
            int buy = 0;
            int sell = 0;
            JsonNode trades = d.path("recent_trades");
            if (trades.isArray()) {
                for (JsonNode t : trades) {
                    String side = text(t, "side");
                    if (side == null) {
                        continue;
                    }
                    if (isBuy(side)) {
                        buy++;
                    } else {
                        sell++;
                    }
                }
            }
            int tradeCount = buy + sell;
            String buyRatio = tradeCount == 0 ? null : ratio(buy, tradeCount);
            String sellRatio = tradeCount == 0 ? null : ratio(sell, tradeCount);

            // Top orderbook levels: pair asks/bids (ascending ask, descending bid).
            List<String[]> asks = new ArrayList<>();
            List<String[]> bids = new ArrayList<>();
            JsonNode ob = d.path("orderbook");
            if (ob.isArray()) {
                for (JsonNode lv : ob) {
                    String price = num(lv, "price");
                    String size = num(lv, "size");
                    String side = text(lv, "side");
                    if (price == null || side == null) {
                        continue;
                    }
                    if (isAsk(side)) {
                        asks.add(new String[] {price, size});
                    } else {
                        bids.add(new String[] {price, size});
                    }
                }
            }
            List<ScalpDto.OrderbookLevel> levels = new ArrayList<>();
            int rows = Math.max(asks.size(), bids.size());
            for (int i = 0; i < rows; i++) {
                String[] a = i < asks.size() ? asks.get(i) : new String[] {null, null};
                String[] bd = i < bids.size() ? bids.get(i) : new String[] {null, null};
                levels.add(new ScalpDto.OrderbookLevel(a[0], a[1], bd[0], bd[1]));
            }

            return new ScalpDto.Detail(
                    sym, num(d, "scalp_score"), num(d, "spread_ticks"), num(d, "tps"),
                    num(d, "micro_vol"), num(d, "ob_imbalance"), text(d, "wall_state"),
                    d.path("cancel_suspicion").asBoolean(false),
                    buyRatio, sellRatio, tradeCount, levels,
                    TimeUtil.toIso(Instant.now()));
        } catch (RuntimeException e) {
            log.warn("scalp sidecar detail symbol={} failed: {}", symbol, e.getMessage());
            return null;
        }
    }

    private static boolean isBuy(String side) {
        String s = side.toUpperCase();
        return s.startsWith("B") || s.equals("BID");
    }

    private static boolean isAsk(String side) {
        String s = side.toUpperCase();
        return s.startsWith("A") || s.equals("SELL") || s.startsWith("S");
    }

    static String ratio(int part, int total) {
        return BigDecimal.valueOf(part)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .toPlainString();
    }

    /** Numeric field -> plain String (money/qty as String per the contract). */
    private static String num(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull() || !v.isNumber()) {
            return null;
        }
        return v.decimalValue().toPlainString();
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText(null);
        return (s == null || s.isBlank()) ? null : s;
    }
}
