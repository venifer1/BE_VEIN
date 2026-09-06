package com.vein.signal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.market.candle.Candle;
import com.vein.market.candle.CandleRepository;

/**
 * Re-evaluates active signals: expiry (TTL) and ABC A_LOW_BREAK invalidation
 * (부록 E / §10.3). Runs after each detection pass.
 */
@Service
public class SignalStatusTransitionService {

    private static final List<SignalStatus> ACTIVE =
            List.of(SignalStatus.DETECTED, SignalStatus.NEAR_COMPLETION);

    private final PatternSignalRepository signalRepository;
    private final CandleRepository candleRepository;

    public SignalStatusTransitionService(PatternSignalRepository signalRepository,
                                         CandleRepository candleRepository) {
        this.signalRepository = signalRepository;
        this.candleRepository = candleRepository;
    }

    /** ABC/TOP promote to NEAR_COMPLETION when latest price is within this of C target.
     *  5% matches the legacy "C 예상가 근접" filter and the API near_only filter. */
    private static final BigDecimal NEAR_PCT = new BigDecimal("0.05");

    @Transactional
    public void reevaluateActive(Instant now) {
        List<PatternSignal> active = signalRepository.findByStatusIn(ACTIVE);
        for (PatternSignal signal : active) {
            if (signal.getExpiresAt() != null && now.isAfter(signal.getExpiresAt())) {
                signal.setStatus(SignalStatus.EXPIRED);
                continue;
            }
            if (isLowBreakInvalidated(signal)) {
                signal.setStatus(SignalStatus.INVALIDATED);
                continue;
            }
            // DETECTED -> NEAR_COMPLETION when the latest close approaches C target.
            if (signal.getStatus() == SignalStatus.DETECTED && isNearCompletion(signal)) {
                signal.setStatus(SignalStatus.NEAR_COMPLETION);
            }
        }
        signalRepository.saveAll(active);
    }

    /**
     * ABC/TOP only: the latest final candle close is within {@link #NEAR_PCT} of the
     * C target (c_target). Uses the live latest close, not the static detection price.
     */
    private boolean isNearCompletion(PatternSignal signal) {
        SignalType type = signal.getType();
        if (type != SignalType.ABC && type != SignalType.TOP) {
            return false;
        }
        BigDecimal cTarget = signal.getCTarget();
        if (cTarget == null || cTarget.signum() == 0) {
            return false;
        }
        BigDecimal latest = candleRepository
                .findTopByIdInstrumentIdAndIdTimeframeOrderByIdOpenTimeDesc(
                        signal.getInstrumentId(), signal.getTimeframe())
                .map(Candle::getClose)
                .orElse(null);
        if (latest == null || latest.signum() == 0) {
            return false;
        }
        BigDecimal rel = cTarget.subtract(latest).abs()
                .divide(latest.abs(), 8, java.math.RoundingMode.HALF_UP);
        return rel.compareTo(NEAR_PCT) <= 0;
    }

    /**
     * Low-break invalidation for ABC (A_LOW_BREAK) and TOP (B_LOW_BREAK): a final
     * candle after the anchor whose low breaks below the invalidation price.
     */
    private boolean isLowBreakInvalidated(PatternSignal signal) {
        String rule = signal.getInvalidationRule();
        boolean lowBreakRule = AbcInvalidation.A_LOW_BREAK.equals(rule)
                || AbcInvalidation.B_LOW_BREAK.equals(rule);
        if (!lowBreakRule || signal.getInvalidationPrice() == null) {
            return false;
        }
        BigDecimal aLow = signal.getInvalidationPrice();
        Instant anchor = signal.getAnchorCandleTime();

        List<Candle> candles = candleRepository
                .findByIdInstrumentIdAndIdTimeframeOrderByIdOpenTimeAsc(
                        signal.getInstrumentId(), signal.getTimeframe());

        for (Candle c : candles) {
            if (!c.isFinal()) {
                continue;
            }
            if (c.getId().getOpenTime().isAfter(anchor)
                    && c.getLow() != null && c.getLow().compareTo(aLow) < 0) {
                return true;
            }
        }
        return false;
    }

    private static final class AbcInvalidation {
        static final String A_LOW_BREAK = "A_LOW_BREAK";
        static final String B_LOW_BREAK = "B_LOW_BREAK";

        private AbcInvalidation() {
        }
    }
}
