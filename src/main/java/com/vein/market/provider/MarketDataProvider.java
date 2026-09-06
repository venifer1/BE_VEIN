package com.vein.market.provider;

import java.time.Instant;
import java.util.List;

import com.vein.common.Timeframe;

/**
 * Pluggable market-data source abstraction (부록 D-1).
 */
public interface MarketDataProvider {

    /** Provider code stored in the {@code provider} column, e.g. {@code UPBIT}. */
    String code();

    List<InstrumentInfo> listInstruments();

    List<RawCandle> fetchCandles(String providerSymbol, Timeframe tf, int count, Instant to);
}
