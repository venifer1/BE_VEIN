package com.vein.derivatives;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vein.common.TimeUtil;
import com.vein.notification.NotificationService;
import com.vein.user.UserRepository;
import com.vein.user.UserStatus;

import lombok.extern.slf4j.Slf4j;

/**
 * Normalizes Binance force-order frames and retains a bounded recent feed.
 */
@Service
@Slf4j
public class LiquidationService {

    private static final int MAX_EVENTS = 500;
    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
    private static final String TOPIC = "/topic/liquidations";

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messaging;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final Duration spikeAlertCooldown;
    private final Deque<LiquidationEvent> events = new ArrayDeque<>();

    private volatile boolean connected;
    private volatile Instant lastEventAt;
    private volatile Instant lastSpikeAlertAt;
    private volatile String lastSpikeAlertLevel = "NORMAL";

    public LiquidationService(ObjectMapper objectMapper,
                              SimpMessagingTemplate messaging,
                              NotificationService notificationService,
                              UserRepository userRepository,
                              @Value("${vein.liquidation.spike-alert-cooldown-sec:1800}")
                              long spikeAlertCooldownSec) {
        this.objectMapper = objectMapper;
        this.messaging = messaging;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.spikeAlertCooldown = Duration.ofSeconds(Math.max(60, spikeAlertCooldownSec));
    }

    public record LiquidationEvent(
            String symbol,
            String base,
            String side,
            String positionSide,
            String price,
            String quantity,
            String notionalUsd,
            String status,
            String eventAt) {
    }

    public record Snapshot(
            boolean connected,
            String lastEventAt,
            int count,
            String totalNotionalUsd,
            String longLiquidationUsd,
            String shortLiquidationUsd,
            List<LiquidationEvent> events) {
    }

    public record WindowSummary(
            String window,
            int count,
            String totalNotionalUsd,
            String longLiquidationUsd,
            String shortLiquidationUsd,
            String maxEventUsd,
            boolean partial) {
    }

    public record SpikeSummary(
            String level,
            String recent5mUsd,
            String baseline5mUsd,
            String ratio) {
    }

    public record Aggregation(
            boolean connected,
            String lastEventAt,
            List<WindowSummary> windows,
            SpikeSummary spike) {
    }

    /**
     * Accepts one raw Binance frame. Malformed and non-USD-M frames are ignored.
     */
    public void accept(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode data = root.has("data") ? root.path("data") : root;
            if (!"forceOrder".equals(data.path("e").asText())) {
                return;
            }
            // The merged stream can include COIN-M after Binance's CM migration.
            if (data.has("st") && data.path("st").asInt(1) != 1) {
                return;
            }
            JsonNode order = data.path("o");
            String symbol = text(order, "s");
            String side = text(order, "S");
            BigDecimal price = decimal(order, "ap");
            if (price == null || price.signum() <= 0) {
                price = decimal(order, "p");
            }
            BigDecimal quantity = decimal(order, "z");
            if (quantity == null || quantity.signum() <= 0) {
                quantity = decimal(order, "q");
            }
            if (symbol == null || side == null || price == null || quantity == null) {
                return;
            }

            long eventMillis = data.path("E").asLong(order.path("T").asLong(0L));
            Instant eventAt = eventMillis > 0 ? Instant.ofEpochMilli(eventMillis) : Instant.now();
            BigDecimal notional = price.multiply(quantity, MC);
            LiquidationEvent event = new LiquidationEvent(
                    symbol,
                    baseSymbol(symbol),
                    side,
                    "SELL".equals(side) ? "LONG" : "SHORT",
                    price.toPlainString(),
                    quantity.toPlainString(),
                    notional.toPlainString(),
                    text(order, "X"),
                    TimeUtil.toIso(eventAt));

            synchronized (events) {
                events.addFirst(event);
                while (events.size() > MAX_EVENTS) {
                    events.removeLast();
                }
            }
            lastEventAt = eventAt;
            messaging.convertAndSend(TOPIC, event);
            maybeAlertSpike(eventAt);
        } catch (Exception e) {
            log.debug("ignored malformed Binance liquidation frame: {}", e.getMessage());
        }
    }

    public Snapshot snapshot(int limit, BigDecimal minNotional, String symbol) {
        int cappedLimit = Math.min(Math.max(limit, 1), 200);
        BigDecimal threshold = minNotional == null ? BigDecimal.ZERO : minNotional.max(BigDecimal.ZERO);
        String normalizedSymbol = normalizeSymbol(symbol);
        List<LiquidationEvent> selected = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal longs = BigDecimal.ZERO;
        BigDecimal shorts = BigDecimal.ZERO;

        synchronized (events) {
            for (LiquidationEvent event : events) {
                if (normalizedSymbol != null
                        && !event.symbol().equals(normalizedSymbol)
                        && !event.base().equals(normalizedSymbol)) {
                    continue;
                }
                BigDecimal notional = new BigDecimal(event.notionalUsd());
                if (notional.compareTo(threshold) < 0) {
                    continue;
                }
                total = total.add(notional);
                if ("LONG".equals(event.positionSide())) {
                    longs = longs.add(notional);
                } else {
                    shorts = shorts.add(notional);
                }
                if (selected.size() < cappedLimit) {
                    selected.add(event);
                }
            }
        }
        return new Snapshot(
                connected,
                TimeUtil.toIso(lastEventAt),
                selected.size(),
                total.toPlainString(),
                longs.toPlainString(),
                shorts.toPlainString(),
                selected);
    }

    public Aggregation aggregation(String symbol) {
        return aggregation(symbol, Instant.now());
    }

    Aggregation aggregation(String symbol, Instant now) {
        String normalizedSymbol = normalizeSymbol(symbol);
        List<LiquidationEvent> copied;
        synchronized (events) {
            copied = events.stream()
                    .filter(event -> matchesSymbol(event, normalizedSymbol))
                    .toList();
        }
        return new Aggregation(
                connected,
                TimeUtil.toIso(lastEventAt),
                List.of(
                        summarize(copied, now, Duration.ofHours(1), "1h"),
                        summarize(copied, now, Duration.ofHours(24), "24h")),
                spike(copied, now));
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    private void maybeAlertSpike(Instant now) {
        Aggregation aggregation = aggregation(null, now);
        SpikeSummary spike = aggregation.spike();
        if ("NORMAL".equals(spike.level())) {
            lastSpikeAlertLevel = "NORMAL";
            return;
        }
        Instant previous = lastSpikeAlertAt;
        boolean escalated = severity(spike.level()) > severity(lastSpikeAlertLevel);
        if (!escalated && previous != null && previous.plus(spikeAlertCooldown).isAfter(now)) {
            return;
        }

        String title = com.vein.notification.NotificationDigest.LIQUIDATION_SPIKE_PREFIX + " " + spike.level();
        String body = "Last 5m " + spike.recent5mUsd()
                + " USD, baseline " + spike.baseline5mUsd()
                + " USD, ratio " + spike.ratio() + "x";
        try {
            userRepository.findByStatus(UserStatus.APPROVED)
                    .forEach(user -> notificationService.createSystemInApp(user.getId(), title, body));
            lastSpikeAlertAt = now;
            lastSpikeAlertLevel = spike.level();
        } catch (RuntimeException e) {
            log.warn("failed to create liquidation spike notifications: {}", e.getMessage());
        }
    }

    private static int severity(String level) {
        return switch (level) {
            case "HIGH" -> 2;
            case "MEDIUM" -> 1;
            default -> 0;
        };
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return symbol.trim().toUpperCase(Locale.ROOT).replace("KRW-", "");
    }

    private static boolean matchesSymbol(LiquidationEvent event, String normalizedSymbol) {
        return normalizedSymbol == null
                || event.symbol().equals(normalizedSymbol)
                || event.base().equals(normalizedSymbol);
    }

    private static WindowSummary summarize(
            List<LiquidationEvent> source, Instant now, Duration duration, String label) {
        Instant from = now.minus(duration);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal longs = BigDecimal.ZERO;
        BigDecimal shorts = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        int count = 0;
        Instant oldest = null;

        for (LiquidationEvent event : source) {
            Instant eventAt = Instant.parse(event.eventAt());
            if (oldest == null || eventAt.isBefore(oldest)) {
                oldest = eventAt;
            }
            if (eventAt.isBefore(from) || eventAt.isAfter(now)) {
                continue;
            }
            BigDecimal notional = new BigDecimal(event.notionalUsd());
            total = total.add(notional);
            max = max.max(notional);
            count++;
            if ("LONG".equals(event.positionSide())) {
                longs = longs.add(notional);
            } else {
                shorts = shorts.add(notional);
            }
        }
        boolean partial = oldest == null || oldest.isAfter(from);
        return new WindowSummary(
                label,
                count,
                total.toPlainString(),
                longs.toPlainString(),
                shorts.toPlainString(),
                max.toPlainString(),
                partial);
    }

    private static SpikeSummary spike(List<LiquidationEvent> source, Instant now) {
        Instant recentFrom = now.minus(Duration.ofMinutes(5));
        Instant baselineFrom = now.minus(Duration.ofMinutes(60));
        BigDecimal recent = BigDecimal.ZERO;
        BigDecimal prior55m = BigDecimal.ZERO;
        for (LiquidationEvent event : source) {
            Instant eventAt = Instant.parse(event.eventAt());
            if (eventAt.isAfter(now) || eventAt.isBefore(baselineFrom)) {
                continue;
            }
            BigDecimal notional = new BigDecimal(event.notionalUsd());
            if (!eventAt.isBefore(recentFrom)) {
                recent = recent.add(notional);
            } else {
                prior55m = prior55m.add(notional);
            }
        }
        BigDecimal baseline5m = prior55m.divide(BigDecimal.valueOf(11), MC);
        BigDecimal ratio = baseline5m.signum() == 0
                ? BigDecimal.ZERO
                : recent.divide(baseline5m, MC);
        String level = "NORMAL";
        if (recent.compareTo(new BigDecimal("100000")) >= 0 && ratio.compareTo(new BigDecimal("5")) >= 0) {
            level = "HIGH";
        } else if (recent.compareTo(new BigDecimal("50000")) >= 0
                && ratio.compareTo(new BigDecimal("2")) >= 0) {
            level = "MEDIUM";
        }
        return new SpikeSummary(
                level,
                recent.toPlainString(),
                baseline5m.toPlainString(),
                ratio.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private static String baseSymbol(String symbol) {
        String upper = symbol.toUpperCase(Locale.ROOT);
        for (String quote : List.of("USDT", "USDC", "BUSD", "USD")) {
            if (upper.endsWith(quote) && upper.length() > quote.length()) {
                return upper.substring(0, upper.length() - quote.length());
            }
        }
        return upper;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
