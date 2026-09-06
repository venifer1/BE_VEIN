package com.vein.signal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vein.alert.AlertEvaluationService;
import com.vein.common.Timeframe;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.market.candle.Candle;
import com.vein.market.candle.CandleRepository;
import com.vein.pattern.abc.AbcDetector;
import com.vein.pattern.abc.AbcPattern;
import com.vein.pattern.imalol.ImalolDetector;
import com.vein.pattern.imalol.ImalolPattern;
import com.vein.pattern.top.TopDetector;
import com.vein.pattern.top.TopPattern;

/**
 * Runs the pure-Java pattern detectors (ABC / TOP / IMALOL) over
 * an instrument's FINAL candles and persists new {@link PatternSignal}s +
 * {@link SignalEvidence}. Idempotent via the signal_key unique constraint
 * (부록 E / §10.1); rule_id distinguishes the detector within the key.
 */
@Service
public class SignalDetectionService {

    /** TTL in bars before a DETECTED signal expires, per rule. */
    private static final int ABC_TTL_BARS = 30;
    private static final int TOP_TTL_BARS = 30;
    private static final int IMALOL_TTL_BARS = 10;

    private final CandleRepository candleRepository;
    private final PatternSignalRepository signalRepository;
    private final SignalEvidenceRepository evidenceRepository;
    private final InstrumentRepository instrumentRepository;
    private final ObjectMapper objectMapper;
    private final AlertEvaluationService alertEvaluationService;

    private final AbcDetector abcDetector = new AbcDetector();
    private final TopDetector topDetector = new TopDetector();
    private final ImalolDetector imalolDetector = new ImalolDetector();

    public SignalDetectionService(CandleRepository candleRepository,
                                  PatternSignalRepository signalRepository,
                                  SignalEvidenceRepository evidenceRepository,
                                  InstrumentRepository instrumentRepository,
                                  ObjectMapper objectMapper,
                                  AlertEvaluationService alertEvaluationService) {
        this.candleRepository = candleRepository;
        this.signalRepository = signalRepository;
        this.evidenceRepository = evidenceRepository;
        this.instrumentRepository = instrumentRepository;
        this.objectMapper = objectMapper;
        this.alertEvaluationService = alertEvaluationService;
    }

    /**
     * Scan one instrument+timeframe with all four detectors and persist any newly
     * detected signals.
     *
     * @return number of newly created signals
     */
    @Transactional
    public int scanInstrument(Long instrumentId, Timeframe tf) {
        List<Candle> all = candleRepository
                .findByIdInstrumentIdAndIdTimeframeOrderByIdOpenTimeAsc(instrumentId, tf.code());

        List<Candle> candles = all.stream().filter(Candle::isFinal).toList();
        if (candles.isEmpty()) {
            return 0;
        }
        Instant detectedAt = openTimeOf(candles, candles.size() - 1);
        BigDecimal currentPrice = candles.get(candles.size() - 1).getClose();
        String market = instrumentRepository.findById(instrumentId)
                .map(Instrument::getMarket).orElse(null);

        int created = 0;
        for (AbcPattern p : abcDetector.detect(candles)) {
            if (createAbcSignal(instrumentId, market, tf, candles, p, detectedAt, currentPrice)) {
                created++;
            }
        }
        for (TopPattern p : topDetector.detect(candles)) {
            if (createTopSignal(instrumentId, market, tf, candles, p, detectedAt, currentPrice)) {
                created++;
            }
        }
        for (ImalolPattern p : imalolDetector.detect(candles)) {
            if (createImalolSignal(instrumentId, market, tf, candles, p, detectedAt, currentPrice)) {
                created++;
            }
        }
        return created;
    }

    private boolean createAbcSignal(Long instrumentId, String market, Timeframe tf,
                                    List<Candle> candles, AbcPattern p, Instant detectedAt,
                                    BigDecimal currentPrice) {
        Instant anchor = openTimeOf(candles, p.idxB());
        String signalKey = PatternSignal.buildSignalKey(
                AbcDetector.ALGORITHM_VERSION, AbcDetector.RULE_ID, instrumentId, tf.code(), anchor);
        if (signalRepository.existsBySignalKey(signalKey)) {
            return false;
        }

        PatternSignal signal = PatternSignal.builder()
                .instrumentId(instrumentId)
                .market(market)
                .type(SignalType.ABC)
                .timeframe(tf.code())
                .status(SignalStatus.DETECTED)
                .score(BigDecimal.valueOf(p.score()))
                .cTarget(BigDecimal.valueOf(p.c100()))
                .currentPrice(currentPrice)
                .detectedAt(detectedAt)
                .anchorCandleTime(anchor)
                .algorithmVersion(AbcDetector.ALGORITHM_VERSION)
                .ruleId(AbcDetector.RULE_ID)
                .invalidationRule(AbcDetector.INVALIDATION_RULE)
                .invalidationPrice(BigDecimal.valueOf(p.pAVal()))
                .expiresAt(anchor.plus(tf.duration().multipliedBy(ABC_TTL_BARS)))
                .signalKey(signalKey)
                .build();

        List<SignalEvidence> evidence = List.of(
                evidence(0, "PIVOT_0", p.p0Val(), openTimeOf(candles, p.idx0()), "idx", p.idx0()),
                evidence(1, "PIVOT_A", p.pAVal(), openTimeOf(candles, p.idxA()), "idx", p.idxA()),
                evidence(2, "PIVOT_B", p.pBVal(), openTimeOf(candles, p.idxB()), "idx", p.idxB()),
                evidence(3, "C_TARGET", p.c100(), anchor, "idx", p.idxB()));

        return persistAndEvaluate(signal, evidence);
    }

    private boolean createTopSignal(Long instrumentId, String market, Timeframe tf,
                                    List<Candle> candles, TopPattern p, Instant detectedAt,
                                    BigDecimal currentPrice) {
        Instant anchor = openTimeOf(candles, p.idxB());
        String signalKey = PatternSignal.buildSignalKey(
                TopDetector.ALGORITHM_VERSION, TopDetector.RULE_ID, instrumentId, tf.code(), anchor);
        if (signalRepository.existsBySignalKey(signalKey)) {
            return false;
        }

        PatternSignal signal = PatternSignal.builder()
                .instrumentId(instrumentId)
                .market(market)
                .type(SignalType.TOP)
                .timeframe(tf.code())
                .status(SignalStatus.DETECTED)
                .score(BigDecimal.valueOf(p.score()))
                .cTarget(BigDecimal.valueOf(p.c100()))
                .currentPrice(currentPrice)
                .detectedAt(detectedAt)
                .anchorCandleTime(anchor)
                .algorithmVersion(TopDetector.ALGORITHM_VERSION)
                .ruleId(TopDetector.RULE_ID)
                .invalidationRule(TopDetector.INVALIDATION_RULE)
                .invalidationPrice(BigDecimal.valueOf(p.pBVal()))
                .expiresAt(anchor.plus(tf.duration().multipliedBy(TOP_TTL_BARS)))
                .signalKey(signalKey)
                .build();

        List<SignalEvidence> evidence = List.of(
                evidence(0, "PIVOT_0", p.p0Val(), openTimeOf(candles, p.idx0()), "idx", p.idx0()),
                evidence(1, "PIVOT_A", p.pAVal(), openTimeOf(candles, p.idxA()), "idx", p.idxA()),
                evidence(2, "PIVOT_B", p.pBVal(), openTimeOf(candles, p.idxB()), "idx", p.idxB()),
                evidence(3, "C_TARGET", p.c100(), anchor, "idx", p.idxB()));

        return persistAndEvaluate(signal, evidence);
    }

    private boolean createImalolSignal(Long instrumentId, String market, Timeframe tf,
                                       List<Candle> candles, ImalolPattern p, Instant detectedAt,
                                       BigDecimal currentPrice) {
        Instant anchor = openTimeOf(candles, p.anchorIdx());
        String signalKey = PatternSignal.buildSignalKey(
                ImalolDetector.ALGORITHM_VERSION, ImalolDetector.RULE_ID, instrumentId, tf.code(), anchor);
        if (signalRepository.existsBySignalKey(signalKey)) {
            return false;
        }

        PatternSignal signal = PatternSignal.builder()
                .instrumentId(instrumentId)
                .market(market)
                .type(SignalType.IMALOL)
                .timeframe(tf.code())
                .status(SignalStatus.DETECTED)
                .score(BigDecimal.valueOf(p.score()))
                .cTarget(nullableBd(p.projectedClose()))
                .currentPrice(currentPrice)
                .detectedAt(detectedAt)
                .anchorCandleTime(anchor)
                .algorithmVersion(ImalolDetector.ALGORITHM_VERSION)
                .ruleId(ImalolDetector.RULE_ID)
                .expiresAt(anchor.plus(tf.duration().multipliedBy(IMALOL_TTL_BARS)))
                .signalKey(signalKey)
                .build();

        List<SignalEvidence> evidence = new ArrayList<>();
        // Bollinger band sampled at the anchor candle.
        evidence.add(bandEvidence(0, "BOLL_UPPER", p.bollUpper(), anchor));
        evidence.add(bandEvidence(1, "BOLL_MID", p.bollMid(), anchor));
        evidence.add(bandEvidence(2, "BOLL_LOWER", p.bollLower(), anchor));
        // One MATCH_BOX evidence per matched 2-candle box (box bounds in JSONB payload).
        int seq = 3;
        for (ImalolPattern.MatchBox box : p.boxes()) {
            evidence.add(SignalEvidence.builder()
                    .evidenceType("MATCH_BOX")
                    .sequenceNo(seq++)
                    .price(nullableBd(box.curClose()))
                    .candleTime(openTimeOf(candles, box.curIdx()))
                    .payload(matchBoxPayload(candles, box))
                    .build());
        }

        return persistAndEvaluate(signal, evidence);
    }

    /**
     * Persist a new signal + evidence and, on success, fan out alert evaluation
     * within the same transaction (cooldown/dedup honoured by the alert service).
     *
     * @return {@code true} if a new signal was created
     */
    private boolean persistAndEvaluate(PatternSignal signal, List<SignalEvidence> evidence) {
        PatternSignal saved = persist(signal, evidence);
        if (saved == null) {
            return false;
        }
        alertEvaluationService.onSignalCreated(saved);
        return true;
    }

    private PatternSignal persist(PatternSignal signal, List<SignalEvidence> evidence) {
        try {
            PatternSignal saved = signalRepository.saveAndFlush(signal);
            List<SignalEvidence> rows = new ArrayList<>(evidence.size());
            for (SignalEvidence e : evidence) {
                rows.add(SignalEvidence.builder()
                        .signalId(saved.getId())
                        .evidenceType(e.getEvidenceType())
                        .sequenceNo(e.getSequenceNo())
                        .price(e.getPrice())
                        .candleTime(e.getCandleTime())
                        .payload(e.getPayload())
                        .build());
            }
            evidenceRepository.saveAll(rows);
            return saved;
        } catch (DataIntegrityViolationException race) {
            // Another node created the same signal_key first: treat as skip.
            return null;
        }
    }

    private SignalEvidence evidence(int seq, String type, double price, Instant candleTime,
                                    String payloadKey, int payloadVal) {
        return SignalEvidence.builder()
                .evidenceType(type)
                .sequenceNo(seq)
                .price(BigDecimal.valueOf(price))
                .candleTime(candleTime)
                .payload(payload(payloadKey, payloadVal))
                .build();
    }

    private SignalEvidence bandEvidence(int seq, String type, double price, Instant candleTime) {
        return SignalEvidence.builder()
                .evidenceType(type)
                .sequenceNo(seq)
                .price(nullableBd(price))
                .candleTime(candleTime)
                .payload("{}")
                .build();
    }

    private String payload(String key, int value) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put(key, value);
        return writeJson(node);
    }

    private String matchBoxPayload(List<Candle> candles, ImalolPattern.MatchBox box) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("prev_idx", box.prevIdx());
        node.put("cur_idx", box.curIdx());
        node.put("prev_high", box.prevHigh());
        node.put("prev_low", box.prevLow());
        node.put("cur_high", box.curHigh());
        node.put("cur_low", box.curLow());
        node.put("cur_close", box.curClose());
        node.put("from", openTimeOf(candles, box.prevIdx()).toString());
        node.put("to", openTimeOf(candles, box.curIdx()).toString());
        return writeJson(node);
    }

    private String writeJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private BigDecimal nullableBd(double v) {
        return Double.isNaN(v) ? null : BigDecimal.valueOf(v);
    }

    private Instant openTimeOf(List<Candle> candles, int idx) {
        return candles.get(idx).getId().getOpenTime();
    }
}
