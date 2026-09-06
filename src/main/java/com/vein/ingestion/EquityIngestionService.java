package com.vein.ingestion;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.Timeframe;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.instrument.ProviderSymbol;
import com.vein.instrument.ProviderSymbolRepository;
import com.vein.market.candle.Candle;
import com.vein.market.candle.CandleId;
import com.vein.market.candle.CandleRepository;
import com.vein.market.provider.RawCandle;
import com.vein.market.provider.equity.KrEquityProvider;
import com.vein.market.provider.equity.UsEquityProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * Candle ingestion for EQUITY markets (US via {@link UsEquityProvider}/YFINANCE,
 * KOSPI/KOSDAQ via {@link KrEquityProvider}/PYKRX). The crypto {@link IngestionService}
 * only polls {@code market=CRYPTO} via the {@code @Primary} Upbit provider, so
 * equity instruments (provider_symbols YFINANCE/PYKRX) were never collected; this
 * service fills that gap.
 *
 * <p>Equities only support {@code 1d/3d/1w}. Each instrument is fetched via its
 * equity provider (which calls the Python sidecar and falls back to a synthetic
 * stub on failure), so this service NEVER hits Upbit. Per-instrument try/catch +
 * continue: one bad symbol cannot abort the rest. Each (market, timeframe) sweep
 * records an {@link IngestionRun}. Upsert is by primary key, so re-running is
 * idempotent.
 */
@Service
@Slf4j
public class EquityIngestionService {

    private static final String JOB_EQUITY = "EQUITY_UPDATE";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String ACTIVE = "ACTIVE";
    private static final int BATCH = 450;

    /** Equity markets and the timeframes they support. */
    private static final List<String> EQUITY_MARKETS = List.of("US", "KOSPI", "KOSDAQ");
    private static final List<Timeframe> EQUITY_TIMEFRAMES =
            List.of(Timeframe.D1, Timeframe.D3, Timeframe.W1);

    private final UsEquityProvider usProvider;
    private final KrEquityProvider krProvider;
    private final InstrumentRepository instrumentRepository;
    private final ProviderSymbolRepository providerSymbolRepository;
    private final CandleRepository candleRepository;
    private final IngestionRunRepository ingestionRunRepository;

    public EquityIngestionService(UsEquityProvider usProvider,
                                  KrEquityProvider krProvider,
                                  InstrumentRepository instrumentRepository,
                                  ProviderSymbolRepository providerSymbolRepository,
                                  CandleRepository candleRepository,
                                  IngestionRunRepository ingestionRunRepository) {
        this.usProvider = usProvider;
        this.krProvider = krProvider;
        this.instrumentRepository = instrumentRepository;
        this.providerSymbolRepository = providerSymbolRepository;
        this.candleRepository = candleRepository;
        this.ingestionRunRepository = ingestionRunRepository;
    }

    /** Update every equity market across all equity timeframes. */
    public void updateAll() {
        for (Timeframe tf : EQUITY_TIMEFRAMES) {
            for (String market : EQUITY_MARKETS) {
                try {
                    update(market, tf);
                } catch (RuntimeException e) {
                    log.warn("equity ingestion: {} {} failed: {}", market, tf.code(), e.getMessage());
                }
            }
        }
    }

    /**
     * Fetch and upsert candles for every ACTIVE instrument in one equity market
     * and timeframe via the equity provider for that market (YFINANCE/PYKRX).
     */
    @Transactional
    public void update(String market, Timeframe tf) {
        var provider = providerFor(market);
        String providerCode = provider.code();

        Instant now = Instant.now();
        IngestionRun run = IngestionRun.start(providerCode, JOB_EQUITY, tf.code(), now);
        run = ingestionRunRepository.save(run);

        int fetched = 0;
        int inserted = 0;
        int failures = 0;
        try {
            List<Instrument> instruments = instrumentRepository.findByMarketAndStatus(market, ACTIVE);
            for (Instrument instrument : instruments) {
                ProviderSymbol ps = providerSymbolRepository
                        .findByProviderAndInstrumentId(providerCode, instrument.getId())
                        .orElse(null);
                if (ps == null) {
                    continue;
                }
                // Per-instrument resilience: one bad/delisted ticker must not abort
                // the rest. The provider itself falls back to the synthetic stub on
                // any sidecar failure, so this rarely throws.
                try {
                    List<RawCandle> batch =
                            provider.fetchCandles(ps.getProviderSymbol(), tf, BATCH, null);
                    fetched += batch.size();
                    inserted += upsert(instrument.getId(), tf, providerCode, batch);
                } catch (RuntimeException e) {
                    failures++;
                    log.warn("equity ingestion skipped instrument {} ({}) {} {}: {}",
                            instrument.getId(), ps.getProviderSymbol(), market, tf.code(),
                            e.getMessage());
                }
            }
            run.setFetchedCount(fetched);
            run.setInsertedCount(inserted);
            run.setStatus(STATUS_SUCCESS);
            if (failures > 0) {
                run.setErrorCode(failures + " instrument(s) failed");
            }
            run.setEndedAt(Instant.now());
        } catch (RuntimeException e) {
            run.setFetchedCount(fetched);
            run.setInsertedCount(inserted);
            run.setStatus(STATUS_FAILED);
            run.setErrorCode(e.getClass().getSimpleName());
            run.setEndedAt(Instant.now());
            log.error("equity ingestion failed market={} tf={}", market, tf.code(), e);
            throw e;
        } finally {
            ingestionRunRepository.save(run);
        }
    }

    private com.vein.market.provider.equity.EquityMarketDataProvider providerFor(String market) {
        return "US".equalsIgnoreCase(market) ? usProvider : krProvider;
    }

    /** Insert candles that don't already exist; returns the number inserted. */
    private int upsert(Long instrumentId, Timeframe tf, String providerCode, List<RawCandle> raws) {
        int inserted = 0;
        for (RawCandle raw : raws) {
            CandleId id = new CandleId(instrumentId, tf.code(), raw.openTime(), providerCode);
            if (candleRepository.existsById(id)) {
                continue;
            }
            Candle candle = Candle.of(
                    instrumentId, tf, raw.openTime(), providerCode,
                    raw.open(), raw.high(), raw.low(), raw.close(), raw.volume(), raw.isFinal());
            candleRepository.save(candle);
            inserted++;
        }
        return inserted;
    }
}
