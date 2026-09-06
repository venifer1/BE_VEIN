package com.vein.market.realtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.vein.common.TimeUtil;
import com.vein.market.provider.upbit.UpbitMarketDataProvider;
import com.vein.market.provider.upbit.UpbitTickerResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Pushes live PUBLIC CRYPTO (Upbit KRW) prices to STOMP subscribers on
 * {@code /topic/prices} ~every 2s so the UI updates live instead of polling.
 *
 * <p>Active only when {@code vein.ingestion.enabled=true} so tests / hermetic
 * startup are unaffected (same gating as {@code TerminalScheduler}). Reuses the
 * existing {@link UpbitMarketDataProvider}: each tick is a single logical
 * {@code /v1/ticker} fetch over all KRW markets, already chunked and
 * rate-limited inside the provider — no second Upbit client. A fetch error is
 * caught so it never kills the scheduler.
 */
@Component
@ConditionalOnProperty(name = "vein.ingestion.enabled", havingValue = "true")
@Slf4j
public class PriceBroadcaster {

    private static final String MARKET = "CRYPTO";
    private static final String TOPIC = "/topic/prices";
    /** signed_change_rate is a fraction (0.0123); UI wants a percent (1.23). */
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    private final UpbitMarketDataProvider upbit;
    private final SimpMessagingTemplate messaging;

    /** ISO-8601 UTC of the last successful broadcast (null until first push). */
    private volatile String lastBroadcastAt = null;

    public PriceBroadcaster(UpbitMarketDataProvider upbit, SimpMessagingTemplate messaging) {
        this.upbit = upbit;
        this.messaging = messaging;
    }

    public record PriceTick(String symbol, String price, String change_rate) {
    }

    public record PricePayload(String market, String ts, List<PriceTick> prices) {
    }

    @Scheduled(fixedDelayString = "${vein.realtime.price-ms:2000}")
    public void broadcast() {
        try {
            List<UpbitTickerResponse> tickers = upbit.fetchAllKrwTickers();
            if (tickers.isEmpty()) {
                return;
            }
            List<PriceTick> prices = new ArrayList<>(tickers.size());
            for (UpbitTickerResponse t : tickers) {
                if (t.market() == null || t.tradePrice() == null) {
                    continue;
                }
                String changeRate = t.signedChangeRate() == null
                        ? null
                        : t.signedChangeRate().multiply(PERCENT).toPlainString();
                prices.add(new PriceTick(t.market(), t.tradePrice().toPlainString(), changeRate));
            }
            String ts = TimeUtil.toIso(Instant.now());
            messaging.convertAndSend(TOPIC, new PricePayload(MARKET, ts, prices));
            lastBroadcastAt = ts;
        } catch (RuntimeException e) {
            // Never let a fetch/serialize error kill the scheduler.
            log.warn("price broadcast failed", e);
        }
    }

    /** ISO-8601 UTC of the last successful broadcast, or {@code null} if none yet. */
    public String lastBroadcastAt() {
        return lastBroadcastAt;
    }
}
