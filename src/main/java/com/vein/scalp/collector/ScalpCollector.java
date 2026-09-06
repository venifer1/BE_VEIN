package com.vein.scalp.collector;

import java.util.List;

import com.vein.scalp.core.ScalpInputs.MarketData;

/**
 * Source of live market-microstructure data for the scalp scorer. The legacy
 * implementation (upbit_ws.py) maintains a persistent Upbit WebSocket feeding
 * orderbook + trade events; this interface lets that be swapped in later behind
 * the same contract.
 */
public interface ScalpCollector {

    /**
     * Collect current {@link MarketData} for the top {@code maxMarkets} KRW markets
     * by 24h trade value. {@code nowSec} is the reference time (unix seconds) used
     * for windowed metrics. May return fewer (or an empty list) on provider error.
     */
    List<MarketData> collect(int maxMarkets, double nowSec);
}
