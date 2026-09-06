package com.vein.market.provider.coingecko;

import com.vein.market.provider.ProviderException;

/**
 * Raised when CoinGecko returns HTTP 429. Carries the {@code Retry-After}
 * seconds (best-effort) so callers (supply ingestion) can back off and keep
 * serving the last good snapshot, mirroring supply_tab._CoinGeckoRateLimitError.
 */
public class CoinGeckoRateLimitException extends ProviderException {

    private final int retryAfterSeconds;

    public CoinGeckoRateLimitException(int retryAfterSeconds) {
        super("CoinGecko rate limited (429), Retry-After=" + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
