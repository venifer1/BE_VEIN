package com.vein.scalp.collector;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vein.market.provider.upbit.UpbitMarketDataProvider;
import com.vein.scalp.core.ScalpInputs.MarketData;
import com.vein.scalp.core.ScalpInputs.OrderbookLevel;
import com.vein.scalp.core.ScalpInputs.Trade;

import lombok.extern.slf4j.Slf4j;

/**
 * Real-time Upbit WebSocket scalp collector (per legacy upbit_ws.py) — the exact
 * TODO left in {@link RestPollingScalpCollector}. Maintains a persistent
 * {@code wss://api.upbit.com/websocket/v1} connection subscribed to {@code orderbook}
 * and {@code trade} for the top-N KRW markets, keeping live per-market state in
 * memory. {@link #collect} then reads that state with no network call, so TPS /
 * micro-vol are computed from the continuous tick stream rather than sampled REST
 * slices.
 *
 * <p>Opt-in via {@code vein.scalp.ws-enabled=true}; when on it is {@code @Primary}
 * and replaces the REST poller behind the same {@link ScalpCollector} contract.
 * Upbit pushes messages as <b>binary</b> frames of JSON. Best-effort: any failure
 * degrades to an empty snapshot (the scorer simply skips that cycle) and the
 * connection self-heals on the reconnect tick.
 */
@Component
@Primary
@ConditionalOnProperty(name = "vein.scalp.ws-enabled", havingValue = "true")
@Slf4j
public class WebSocketScalpCollector implements ScalpCollector, DisposableBean {

    /** Keep a little more than the scorer's keepTradeSec (12s) so windows never starve. */
    private static final double KEEP_TRADE_SEC = 20.0;
    /** Hard cap on buffered trades per market (memory guard for very hot markets). */
    private static final int MAX_TRADES = 600;

    private final UpbitMarketDataProvider upbit;
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI endpoint;
    private final int subscribeMarkets;
    private final HttpClient httpClient;
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private volatile WebSocket socket;
    private volatile List<String> markets = List.of();
    private final Map<String, MarketState> state = new ConcurrentHashMap<>();

    public WebSocketScalpCollector(
            UpbitMarketDataProvider upbit,
            @Value("${vein.scalp.ws-url:wss://api.upbit.com/websocket/v1}") String wsUrl,
            @Value("${vein.scalp.ws-subscribe-markets:50}") int subscribeMarkets) {
        this.upbit = upbit;
        this.endpoint = URI.create(wsUrl);
        this.subscribeMarkets = subscribeMarkets;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Live per-market microstructure state, mutated from the WS listener thread. */
    private static final class MarketState {
        volatile List<OrderbookLevel> levels = List.of();
        final Deque<Trade> trades = new ArrayDeque<>();
        volatile double lastTradeTs;
        volatile double lastTradePrice;
    }

    @Scheduled(fixedDelayString = "${vein.scalp.reconnect-ms:5000}")
    public void ensureConnected() {
        WebSocket current = socket;
        if (current != null) {
            try {
                current.sendPing(ByteBuffer.allocate(0)); // keepalive (Upbit drops idle conns ~120s)
            } catch (RuntimeException ignore) {
                // a ping may already be pending; harmless, the stream itself keeps it alive
            }
            return;
        }
        if (!connecting.compareAndSet(false, true)) {
            return; // a connect attempt is already in flight
        }
        List<String> top;
        try {
            top = upbit.fetchTopKrwMarketsByValue(subscribeMarkets);
        } catch (RuntimeException e) {
            log.warn("scalp WS: top-market ranking failed: {}", e.getMessage());
            connecting.set(false);
            return;
        }
        if (top.isEmpty()) {
            connecting.set(false);
            return;
        }
        this.markets = top;
        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(endpoint, new Listener(top))
                .whenComplete((ws, error) -> {
                    connecting.set(false);
                    if (error != null) {
                        log.warn("scalp WS connect failed: {}", error.getMessage());
                    }
                });
    }

    @Override
    public List<MarketData> collect(int maxMarkets, double nowSec) {
        List<String> subs = markets;
        if (subs.isEmpty()) {
            return List.of();
        }
        int limit = Math.min(maxMarkets, subs.size());
        List<MarketData> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            String market = subs.get(i);
            MarketState ms = state.get(market);
            if (ms == null || ms.levels.isEmpty()) {
                continue; // no book yet — skip until the stream warms up
            }
            List<Trade> recent = snapshotTrades(ms, nowSec);
            out.add(new MarketData(market, null, ms.levels, recent, ms.lastTradeTs, ms.lastTradePrice));
        }
        return out;
    }

    /** Copy + prune trades older than the keep window under the deque lock. */
    private static List<Trade> snapshotTrades(MarketState ms, double nowSec) {
        double cutoff = nowSec - KEEP_TRADE_SEC;
        synchronized (ms.trades) {
            while (!ms.trades.isEmpty() && ms.trades.peekFirst().tsSec() < cutoff) {
                ms.trades.pollFirst();
            }
            return new ArrayList<>(ms.trades);
        }
    }

    @Override
    public void destroy() {
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            current.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final String subscription;
        private final ByteArrayOutputStream frame = new ByteArrayOutputStream();

        Listener(List<String> codes) {
            this.subscription = buildSubscription(codes);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            log.info("Upbit scalp WS connected ({} markets)", markets.size());
            webSocket.sendText(subscription, true);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            frame.writeBytes(chunk);
            if (last) {
                byte[] msg = frame.toByteArray();
                frame.reset();
                try {
                    apply(mapper.readTree(msg));
                } catch (RuntimeException | java.io.IOException e) {
                    log.debug("scalp WS parse failed: {}", e.getMessage());
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("Upbit scalp WS closed: {} {}", statusCode, reason);
            socket = null;
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("Upbit scalp WS error: {}", error.getMessage());
            socket = null;
        }
    }

    /** Apply one decoded Upbit message (orderbook or trade) to the live state. */
    private void apply(JsonNode node) {
        String type = text(node, "type");
        String code = text(node, "code");
        if (type == null || code == null) {
            return;
        }
        MarketState ms = state.computeIfAbsent(code, k -> new MarketState());
        if ("orderbook".equals(type)) {
            JsonNode units = node.get("orderbook_units");
            if (units != null && units.isArray()) {
                List<OrderbookLevel> levels = new ArrayList<>(units.size());
                for (JsonNode u : units) {
                    double ask = u.path("ask_price").asDouble(0);
                    double bid = u.path("bid_price").asDouble(0);
                    if (ask <= 0 || bid <= 0) {
                        continue;
                    }
                    levels.add(new OrderbookLevel(ask, bid,
                            u.path("ask_size").asDouble(0), u.path("bid_size").asDouble(0)));
                }
                if (!levels.isEmpty()) {
                    ms.levels = levels;
                }
            }
        } else if ("trade".equals(type)) {
            double price = node.path("trade_price").asDouble(0);
            if (price <= 0) {
                return;
            }
            double ts = node.path("trade_timestamp").asDouble(0);
            double tsSec = ts > 0 ? ts / 1000.0 : System.currentTimeMillis() / 1000.0;
            String side = node.path("ask_bid").asText("").toUpperCase();
            double size = node.path("trade_volume").asDouble(0);
            synchronized (ms.trades) {
                ms.trades.addLast(new Trade(tsSec, price, side, size));
                while (ms.trades.size() > MAX_TRADES) {
                    ms.trades.pollFirst();
                }
            }
            ms.lastTradeTs = tsSec;
            ms.lastTradePrice = price;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** Upbit subscription frame: one ticket + orderbook + trade for the given codes. */
    private String buildSubscription(List<String> codes) {
        StringBuilder arr = new StringBuilder();
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0) {
                arr.append(',');
            }
            arr.append('"').append(codes.get(i)).append('"');
        }
        String c = arr.toString();
        return "[{\"ticket\":\"vein-scalp\"},"
                + "{\"type\":\"orderbook\",\"codes\":[" + c + "]},"
                + "{\"type\":\"trade\",\"codes\":[" + c + "]},"
                + "{\"format\":\"DEFAULT\"}]";
    }
}
