package com.vein.market.provider;

/**
 * Raw instrument descriptor returned by a {@link MarketDataProvider}.
 */
public record InstrumentInfo(
        String market,
        String exchange,
        String symbol,
        String koreanName,
        String englishName,
        String quoteCurrency) {
}
