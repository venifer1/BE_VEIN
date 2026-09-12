package com.vein.signal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
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
    /** 저가-이탈 무효화 완화 버퍼(R53). 저가가 기준선보다 이 비율 이상 아래여야 무효. */
    private final BigDecimal invalidationBufferPct;

    public SignalStatusTransitionService(PatternSignalRepository signalRepository,
                                         CandleRepository candleRepository,
                                         @Value("${vein.signal.invalidation-buffer-pct:0.03}")
                                         BigDecimal invalidationBufferPct) {
        this.signalRepository = signalRepository;
        this.candleRepository = candleRepository;
        this.invalidationBufferPct = invalidationBufferPct;
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
                    && breaches(c.getLow(), aLow, invalidationBufferPct)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 저가-이탈 판정(R53 완화). {@code low}가 기준선({@code invalidationPrice})보다 {@code bufferPct}
     * 이상 아래로 내려갔을 때만 참. 즉 임계선 = 기준선 × (1 − buffer). {@code buffer=0}이면 기존처럼
     * 1틱만 깨도 무효. 얕은 꼬리/노이즈성 이탈을 봐준다. 순수 함수(테스트 용이).
     */
    static boolean breaches(BigDecimal low, BigDecimal invalidationPrice, BigDecimal bufferPct) {
        if (low == null || invalidationPrice == null) {
            return false;
        }
        return low.compareTo(thresholdPrice(invalidationPrice, bufferPct)) < 0;
    }

    /** 임계선 = 기준선 × (1 − buffer). {@code breaches}와 실질가 노출이 공유하는 단일 공식. */
    static BigDecimal thresholdPrice(BigDecimal invalidationPrice, BigDecimal bufferPct) {
        BigDecimal buffer = (bufferPct == null || bufferPct.signum() < 0) ? BigDecimal.ZERO : bufferPct;
        return invalidationPrice.multiply(BigDecimal.ONE.subtract(buffer));
    }

    /** 저가-이탈 무효화 완충 비율(R53). 0.03 = 3%. */
    public BigDecimal invalidationBufferPct() {
        return invalidationBufferPct;
    }

    /**
     * 실제 무효화가 발동하는 임계가(기준선 × (1 − buffer)). 저가-이탈 규칙(ABC A저점·TOP B저점)이
     * 아니거나 기준선이 없으면 {@code null} — 완충 개념이 적용되지 않는 규칙엔 실질가가 없다.
     */
    public BigDecimal effectiveInvalidationPrice(PatternSignal signal) {
        String rule = signal.getInvalidationRule();
        boolean lowBreakRule = AbcInvalidation.A_LOW_BREAK.equals(rule)
                || AbcInvalidation.B_LOW_BREAK.equals(rule);
        if (!lowBreakRule || signal.getInvalidationPrice() == null) {
            return null;
        }
        return thresholdPrice(signal.getInvalidationPrice(), invalidationBufferPct);
    }

    private static final class AbcInvalidation {
        static final String A_LOW_BREAK = "A_LOW_BREAK";
        static final String B_LOW_BREAK = "B_LOW_BREAK";

        private AbcInvalidation() {
        }
    }
}
