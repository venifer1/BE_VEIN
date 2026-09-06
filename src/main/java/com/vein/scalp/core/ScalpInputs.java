package com.vein.scalp.core;

import java.util.List;

/** Raw market-microstructure inputs for the scalp scorer (provider-agnostic). */
public final class ScalpInputs {

    private ScalpInputs() {
    }

    /** One orderbook level (best level is index 0). */
    public record OrderbookLevel(double askPrice, double bidPrice, double askSize, double bidSize) {
    }

    /** A recent trade. {@code side} = BID (buy) | ASK (sell); {@code tsSec} unix seconds. */
    public record Trade(double tsSec, double price, String side, double size) {
    }

    /** A market's current orderbook + recent trades. */
    public record MarketData(String market, String name, List<OrderbookLevel> levels,
                             List<Trade> trades, double lastTradeTsSec, double lastTradePrice) {
    }
}
