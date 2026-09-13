package com.vein.ingestion;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.common.Timeframe;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.instrument.ProviderSymbol;
import com.vein.instrument.ProviderSymbolRepository;
import com.vein.market.candle.Candle;
import com.vein.market.candle.CandleId;
import com.vein.market.candle.CandleRepository;
import com.vein.market.provider.MarketDataProvider;
import com.vein.market.provider.RawCandle;

import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates candle ingestion (backfill & poll) via a {@link MarketDataProvider}
 * (부록 D). Each job is recorded as an {@link IngestionRun}.
 */
@Service
@Slf4j
public class IngestionService {

    private static final int BATCH = 200;
    private static final String JOB_BACKFILL = "BACKFILL";
    private static final String JOB_POLL = "POLL";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String ACTIVE = "ACTIVE";

    private final MarketDataProvider provider;
    private final InstrumentRepository instrumentRepository;
    private final ProviderSymbolRepository providerSymbolRepository;
    private final CandleRepository candleRepository;
    private final IngestionRunRepository ingestionRunRepository;

    public IngestionService(MarketDataProvider provider,
                            InstrumentRepository instrumentRepository,
                            ProviderSymbolRepository providerSymbolRepository,
                            CandleRepository candleRepository,
                            IngestionRunRepository ingestionRunRepository) {
        this.provider = provider;
        this.instrumentRepository = instrumentRepository;
        this.providerSymbolRepository = providerSymbolRepository;
        this.candleRepository = candleRepository;
        this.ingestionRunRepository = ingestionRunRepository;
    }

    /**
     * Backfill up to {@code count} candles for an instrument+timeframe, paging
     * backwards via the {@code to} cursor in batches of {@value #BATCH}.
     */
    @Transactional
    public void backfill(Long instrumentId, Timeframe tf, int count) {
        Instant now = Instant.now();
        IngestionRun run = IngestionRun.start(provider.code(), JOB_BACKFILL, tf.code(), now);
        run = ingestionRunRepository.save(run);

        try {
            Instrument instrument = instrumentRepository.findById(instrumentId)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                            "Instrument not found: " + instrumentId));
            String providerSymbol = resolveProviderSymbol(instrumentId);

            int target = Math.max(count, 1);
            int fetched = 0;
            int inserted = 0;
            Instant cursor = null;
            int maxIterations = (target + BATCH - 1) / BATCH;

            for (int i = 0; i < maxIterations && fetched < target; i++) {
                int want = Math.min(BATCH, target - fetched);
                List<RawCandle> batch = provider.fetchCandles(providerSymbol, tf, want, cursor);
                if (batch.isEmpty()) {
                    break;
                }
                fetched += batch.size();
                inserted += upsert(instrument, tf, batch);
                // oldest candle in this ascending batch becomes the next 'to' cursor
                Instant oldest = batch.get(0).openTime();
                cursor = oldest;
                if (batch.size() < want) {
                    break;
                }
            }

            int gaps = countGaps(instrumentId, tf);

            run.setFetchedCount(fetched);
            run.setInsertedCount(inserted);
            run.setGapCount(gaps);
            run.setStatus(STATUS_SUCCESS);
            run.setEndedAt(Instant.now());
        } catch (RuntimeException e) {
            run.setStatus(STATUS_FAILED);
            run.setErrorCode(errorCodeOf(e));
            run.setEndedAt(Instant.now());
            log.error("Backfill failed instrumentId={} tf={}", instrumentId, tf.code(), e);
            throw e;
        } finally {
            ingestionRunRepository.save(run);
        }
    }

    /**
     * Poll the latest {@value #BATCH} candles for every ACTIVE instrument and
     * upsert any new bars.
     */
    @Transactional
    public void poll(Timeframe tf) {
        Instant now = Instant.now();
        IngestionRun run = IngestionRun.start(provider.code(), JOB_POLL, tf.code(), now);
        run = ingestionRunRepository.save(run);

        int fetched = 0;
        int inserted = 0;
        int failures = 0;
        try {
            List<Instrument> instruments =
                    instrumentRepository.findByMarketAndStatus("CRYPTO", ACTIVE);
            for (Instrument instrument : instruments) {
                ProviderSymbol ps = providerSymbolRepository
                        .findByProviderAndInstrumentId(provider.code(), instrument.getId())
                        .orElse(null);
                if (ps == null) {
                    continue;
                }
                // Per-instrument resilience: one bad/delisted symbol (e.g. KRW-MATIC)
                // must not abort polling for the rest of the active instruments.
                try {
                    List<RawCandle> batch =
                            provider.fetchCandles(ps.getProviderSymbol(), tf, BATCH, null);
                    fetched += batch.size();
                    inserted += upsert(instrument, tf, batch);
                } catch (RuntimeException e) {
                    failures++;
                    log.warn("Poll skipped instrument {} ({}) tf={}: {}",
                            instrument.getId(), ps.getProviderSymbol(), tf.code(), e.getMessage());
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
            run.setErrorCode(errorCodeOf(e));
            run.setEndedAt(Instant.now());
            log.error("Poll failed tf={}", tf.code(), e);
            throw e;
        } finally {
            ingestionRunRepository.save(run);
        }
    }

    /** Insert candles that don't already exist; returns the number inserted. */
    int upsert(Instrument instrument, Timeframe tf, List<RawCandle> raws) {
        int inserted = 0;
        for (RawCandle raw : raws) {
            CandleId id = new CandleId(instrument.getId(), tf.code(), raw.openTime(), provider.code());
            if (candleRepository.existsById(id)) {
                continue;
            }
            Candle candle = Candle.of(
                    instrument.getId(), tf, raw.openTime(), provider.code(),
                    raw.open(), raw.high(), raw.low(), raw.close(), raw.volume(), raw.isFinal());
            candleRepository.save(candle);
            inserted++;
        }
        return inserted;
    }

    private String resolveProviderSymbol(Long instrumentId) {
        return providerSymbolRepository
                .findByProviderAndInstrumentId(provider.code(), instrumentId)
                .map(ProviderSymbol::getProviderSymbol)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "No provider symbol for instrument " + instrumentId
                                + " provider " + provider.code()));
    }

    /** Count missing steps between consecutive stored candles. */
    private int countGaps(Long instrumentId, Timeframe tf) {
        List<Candle> all = candleRepository
                .findByIdInstrumentIdAndIdTimeframeOrderByIdOpenTimeAsc(instrumentId, tf.code());
        if (all.size() < 2) {
            return 0;
        }
        Duration step = tf.duration();
        long stepSeconds = step.getSeconds();
        if (stepSeconds <= 0) {
            return 0;
        }
        int gaps = 0;
        for (int i = 1; i < all.size(); i++) {
            long actual = Duration.between(all.get(i - 1).openTime(), all.get(i).openTime())
                    .getSeconds();
            long missing = (actual / stepSeconds) - 1;
            if (missing > 0) {
                gaps += (int) missing;
            }
        }
        return gaps;
    }

    static String errorCodeOf(RuntimeException e) {
        if (e instanceof ApiException api) {
            return api.getErrorCode().name();
        }
        return e.getClass().getSimpleName();
    }
}
