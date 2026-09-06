package com.vein.indicator;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.Timeframe;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentService;
import com.vein.market.candle.Candle;
import com.vein.market.candle.CandleRepository;

/**
 * Computes the terminal indicator summary (GET /instruments/{id}/indicators):
 * RSI(14), MA(5/20/60/120), Bollinger(20,2), MACD(12,26,9). Pure math lives in
 * {@link Indicators}; this just loads closes and shapes the latest values.
 */
@Service
@Transactional(readOnly = true)
public class IndicatorService {

    private static final int LOOKBACK = 300;

    private final CandleRepository candleRepository;
    private final InstrumentService instrumentService;

    public IndicatorService(CandleRepository candleRepository, InstrumentService instrumentService) {
        this.candleRepository = candleRepository;
        this.instrumentService = instrumentService;
    }

    public record IndicatorSummary(
            String timeframe,
            String rsi14,
            String ma5, String ma20, String ma60, String ma120,
            String bollUpper, String bollMiddle, String bollLower,
            String macd, String macdSignal, String macdHistogram) {
    }

    public IndicatorSummary compute(Long instrumentId, String timeframeCode) {
        Instrument instrument = instrumentService.getById(instrumentId);
        Timeframe tf = Timeframe.fromCode(timeframeCode);
        // Validate (market, timeframe) combo.
        if (!tf.isSupportedFor(instrument.getMarket())) {
            throw new com.vein.common.ApiException(com.vein.common.ErrorCode.UNSUPPORTED_TIMEFRAME,
                    tf.code() + " not supported for market " + instrument.getMarket());
        }

        // Latest LOOKBACK candles, newest first -> reverse to ascending closes.
        List<Candle> latest = candleRepository.findLatest(
                instrumentId, tf.code(), null, null, PageRequest.of(0, LOOKBACK));
        List<BigDecimal> closes = latest.stream()
                .sorted((a, b) -> a.openTime().compareTo(b.openTime()))
                .map(Candle::close)
                .toList();

        Indicators.Bollinger boll = Indicators.bollinger(closes, 20, 2.0);
        Indicators.Macd macd = Indicators.macd(closes, 12, 26, 9);

        return new IndicatorSummary(
                tf.code(),
                str(Indicators.last(Indicators.rsi(closes, 14))),
                str(Indicators.lastSma(closes, 5)),
                str(Indicators.lastSma(closes, 20)),
                str(Indicators.lastSma(closes, 60)),
                str(Indicators.lastSma(closes, 120)),
                str(Indicators.last(boll.upper())),
                str(Indicators.last(boll.middle())),
                str(Indicators.last(boll.lower())),
                str(Indicators.last(macd.macd())),
                str(Indicators.last(macd.signal())),
                str(Indicators.last(macd.histogram())));
    }

    private static String str(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }
}
