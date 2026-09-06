package com.vein.market.provider.equity;

import com.vein.market.provider.MarketDataProvider;

/**
 * Marker for equity (stock) market-data providers (US via yfinance, KR via
 * pykrx). These are Python-only libraries with no Java equivalent, so the
 * concrete implementations here are deterministic synthetic stubs.
 *
 * <p>TODO: replace with a real yfinance/pykrx bridge — e.g. a sidecar Python
 * microservice exposing OHLCV over HTTP, or a commercial market-data vendor.
 * The contract and UI work today against the stub; real equity data is a
 * follow-up integration tracked in backend/README.md.
 */
public interface EquityMarketDataProvider extends MarketDataProvider {
}
