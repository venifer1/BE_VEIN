package com.vein.market.provider.equity;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.vein.common.Timeframe;
import com.vein.market.provider.InstrumentInfo;
import com.vein.market.provider.RawCandle;

/**
 * KR equities provider (KOSPI/KOSDAQ; code {@code PYKRX}).
 *
 * <p>Delegates to the Python {@link EquitySidecarClient} (pykrx) for REAL
 * daily/3-day/weekly candles. pykrx resolves a numeric code regardless of board,
 * so KOSPI/KOSDAQ instruments share this provider; the sidecar is queried with
 * {@code market=KOSPI} (board is informational for candle fetch). On ANY sidecar
 * failure it falls back to the deterministic synthetic generator
 * ({@link StubEquityCandles}). The {@code provider} column stays {@code PYKRX}.
 */
@Component
public class KrEquityProvider implements EquityMarketDataProvider {

    public static final String CODE = "PYKRX";
    private static final String MARKET = "KOSPI";

    private final EquitySidecarClient sidecar;

    public KrEquityProvider(EquitySidecarClient sidecar) {
        this.sidecar = sidecar;
    }

    @Override
    public String code() {
        return CODE;
    }

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
        return StubEquityCandles.generate(providerSymbol, tf, count, to);
    }
}
