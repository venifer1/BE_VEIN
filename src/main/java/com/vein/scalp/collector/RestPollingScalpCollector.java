package com.vein.scalp.collector;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.vein.market.provider.upbit.UpbitMarketDataProvider;
import com.vein.market.provider.upbit.UpbitOrderbookResponse;
import com.vein.market.provider.upbit.UpbitTradeResponse;
import com.vein.scalp.core.ScalpInputs.MarketData;
import com.vein.scalp.core.ScalpInputs.OrderbookLevel;
import com.vein.scalp.core.ScalpInputs.Trade;

import lombok.extern.slf4j.Slf4j;

/**
 * REST-polling approximation of the legacy Upbit WebSocket collector. For the top
 * KRW markets by 24h value it pulls the current orderbook (one batched call) and a
 * window of recent trade ticks (per market) via Upbit REST, normalizing them into
 * {@link MarketData} for the pure {@code ScalpCalculator}.
 *
 * <p>This is intentionally an approximation: REST trade ticks give a recent slice
 * rather than the continuous stream a WebSocket provides, so TPS/micro-vol are
 * sampled, not exact. It is sufficient to drive the ranking contract end-to-end
 * without a heavyweight persistent WS client.
 *
 * <p>The real-time replacement now exists: {@link WebSocketScalpCollector} maintains an
 * Upbit WebSocket (orderbook+trade) feed and, when {@code vein.scalp.ws-enabled=true},
 * is {@code @Primary} in place of this bean. This poller remains the default and the
 * fallback when the WS path is disabled.
 */
@Component
@Slf4j
public class RestPollingScalpCollector implements ScalpCollector {

    /** How many recent trade ticks to pull per market (covers the metric windows). */
    private static final int TRADE_TICKS = 100;

    private final UpbitMarketDataProvider upbit;

    public RestPollingScalpCollector(UpbitMarketDataProvider upbit) {
        this.upbit = upbit;
    }

    @Override
    public List<MarketData> collect(int maxMarkets, double nowSec) {
        List<String> markets;
        try {
            markets = upbit.fetchTopKrwMarketsByValue(maxMarkets);
        } catch (RuntimeException e) {
            log.warn("scalp collector: top-market ranking failed: {}", e.getMessage());
            return List.of();
        }
        if (markets.isEmpty()) {
            return List.of();
        }

        // Orderbooks: one batched call for all watched markets.
        List<UpbitOrderbookResponse> orderbooks;
        try {
            orderbooks = upbit.fetchOrderbooks(markets);
        } catch (RuntimeException e) {
            log.warn("scalp collector: orderbook fetch failed: {}", e.getMessage());
            return List.of();
        }

        List<MarketData> out = new ArrayList<>(orderbooks.size());
        for (UpbitOrderbookResponse ob : orderbooks) {
            if (ob.market() == null) {
                continue;
            }
            List<OrderbookLevel> levels = new ArrayList<>();
            if (ob.orderbookUnits() != null) {
                for (UpbitOrderbookResponse.Unit u : ob.orderbookUnits()) {
                    if (u.askPrice() == null || u.bidPrice() == null
                            || u.askPrice().signum() <= 0 || u.bidPrice().signum() <= 0) {
                        continue;
                    }
                    levels.add(new OrderbookLevel(
                            u.askPrice().doubleValue(), u.bidPrice().doubleValue(),
                            size(u.askSize()), size(u.bidSize())));
                }
            }

            List<Trade> trades = new ArrayList<>();
            double lastTradeTs = 0.0;
            double lastTradePrice = 0.0;
            try {
                List<UpbitTradeResponse> ticks = upbit.fetchRecentTrades(ob.market(), TRADE_TICKS);
                for (UpbitTradeResponse t : ticks) {
                    if (t.tradePrice() == null || t.tradePrice().signum() <= 0) {
                        continue;
                    }
                    double ts = t.timestamp() == null ? nowSec : t.timestamp() / 1000.0;
                    trades.add(new Trade(ts, t.tradePrice().doubleValue(),
                            t.askBid() == null ? "" : t.askBid().toUpperCase(), size(t.tradeVolume())));
                    if (ts > lastTradeTs) {
                        lastTradeTs = ts;
                        lastTradePrice = t.tradePrice().doubleValue();
                    }
                }
            } catch (RuntimeException e) {
                log.debug("scalp collector: trades fetch failed for {}: {}", ob.market(), e.getMessage());
            }

            out.add(new MarketData(ob.market(), null, levels, trades, lastTradeTs, lastTradePrice));
        }
        return out;
    }

    private static double size(java.math.BigDecimal v) {
        return v == null ? 0.0 : v.doubleValue();
    }
}
