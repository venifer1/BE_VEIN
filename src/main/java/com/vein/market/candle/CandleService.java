package com.vein.market.candle;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.common.Freshness;
import com.vein.common.TimeUtil;
import com.vein.common.Timeframe;
import com.vein.instrument.InstrumentService;

@Service
@Transactional(readOnly = true)
public class CandleService {

    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;

    private final CandleRepository candleRepository;
    private final InstrumentService instrumentService;

    public CandleService(CandleRepository candleRepository, InstrumentService instrumentService) {
        this.candleRepository = candleRepository;
        this.instrumentService = instrumentService;
    }

    public record CandleResponse(List<CandleDto> candles, String provider, Freshness freshness) {
    }

    public CandleResponse getCandles(Long instrumentId, String timeframeCode,
                                     String from, String to, Integer limit) {
        // 1) instrument must exist
        com.vein.instrument.Instrument instrument = instrumentService.getById(instrumentId);

        // 2) timeframe (throws UNSUPPORTED_TIMEFRAME for unknown codes)
        Timeframe tf = Timeframe.fromCode(timeframeCode);

        // 2b) reject invalid (market, timeframe) combos (e.g. 15m on a US equity)
        if (!tf.isSupportedFor(instrument.getMarket())) {
            throw new ApiException(ErrorCode.UNSUPPORTED_TIMEFRAME,
                    tf.code() + " not supported for market " + instrument.getMarket());
        }

        // 3) parse time bounds
        Instant fromTs = parse(from);
        Instant toTs = parse(to);
        if (fromTs != null && toTs != null && fromTs.isAfter(toTs)) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "from must be <= to");
        }

        // 4) limit bounds
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);

        // 5) fetch latest N within window (newest first), then reverse to ascending
        List<Candle> latest = candleRepository.findLatest(
                instrumentId, tf.code(), fromTs, toTs, PageRequest.of(0, effectiveLimit));

        List<CandleDto> candles = latest.stream()
                .sorted((a, b) -> a.openTime().compareTo(b.openTime()))
                .map(CandleDto::from)
                .toList();

        // 6) freshness from the most recent candle's collected_at
        Candle newest = candleRepository
                .findTopByIdInstrumentIdAndIdTimeframeOrderByIdOpenTimeDesc(instrumentId, tf.code())
                .orElse(null);
        Instant collectedAt = newest == null ? null : newest.getCollectedAt();
        Freshness freshness = TimeUtil.freshness(collectedAt, tf, Instant.now());
        String provider = newest == null ? null : newest.getId().getProvider();

        return new CandleResponse(candles, provider, freshness);
    }

    private Instant parse(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return TimeUtil.parseIso(iso);
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.INVALID_QUERY, "Invalid ISO-8601 timestamp: " + iso);
        }
    }
}
