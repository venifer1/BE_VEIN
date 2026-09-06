package com.vein.market.provider.equity;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.vein.common.Timeframe;
import com.vein.market.provider.InstrumentInfo;
import com.vein.market.provider.RawCandle;

/**
 * US equities provider (code {@code YFINANCE}).
 *
 * <p>Delegates to the Python {@link EquitySidecarClient} (yfinance) for REAL
 * daily/3-day/weekly candles. On ANY sidecar failure (down, timeout, empty) it
 * logs a warning inside the client and falls back to the deterministic synthetic
 * generator ({@link StubEquityCandles}) so the app keeps working. The
 * {@code provider} column stays {@code YFINANCE} either way.
 */
@Component
public class UsEquityProvider implements EquityMarketDataProvider {

    public static final String CODE = "YFINANCE";
    private static final String MARKET = "US";

    private final EquitySidecarClient sidecar;

    public UsEquityProvider(EquitySidecarClient sidecar) {
        this.sidecar = sidecar;
    }

    @Override
    public String code() {
        return CODE;
    }

    /** Instrument discovery is via the V6 seed, not the provider. */
    @Override
    public List<InstrumentInfo> listInstruments() {
        return List.of();
    }

    @Override
    public List<RawCandle> fetchCandles(String providerSymbol, Timeframe tf, int count, Instant to) {
        List<RawCandle> real = sidecar.fetchCandles(MARKET, providerSymbol, tf, count);
        if (!real.isEmpty()) {
            return real;
        }
        // Sidecar unavailable/empty (already logged): keep the app working on the stub.
        return StubEquityCandles.generate(providerSymbol, tf, count, to);
    }
}
