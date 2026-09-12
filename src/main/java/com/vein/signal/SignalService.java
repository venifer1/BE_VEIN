package com.vein.signal;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.CursorUtil;
import com.vein.common.ErrorCode;
import com.vein.common.Timeframe;
import com.vein.common.TimeUtil;
import com.vein.instrument.Instrument;
import com.vein.instrument.InstrumentRepository;
import com.vein.macro.EventRiskService;
import com.vein.macro.MacroDto.EventRisk;
import com.vein.signal.SignalDetailDto.ChartRange;
import com.vein.signal.SignalDetailDto.EvidenceDto;
import com.vein.signal.SignalDetailDto.InvalidationDto;
import com.vein.signal.SignalDto.InstrumentRef;
import com.vein.signal.SignalDto.PivotDates;
import com.vein.watchlist.WatchlistService;

/**
 * Read side for signals: cursor-paginated list and full detail (API_CONTRACT v2 §4).
 */
@Service
@Transactional(readOnly = true)
public class SignalService {

    private static final int PAGE_SIZE = 20;

    private final PatternSignalRepository signalRepository;
    private final SignalEvidenceRepository evidenceRepository;
    private final InstrumentRepository instrumentRepository;
    private final WatchlistService watchlistService;
    private final EventRiskService eventRiskService;

    public SignalService(PatternSignalRepository signalRepository,
                         SignalEvidenceRepository evidenceRepository,
                         InstrumentRepository instrumentRepository,
                         WatchlistService watchlistService,
                         EventRiskService eventRiskService) {
        this.signalRepository = signalRepository;
        this.evidenceRepository = evidenceRepository;
        this.instrumentRepository = instrumentRepository;
        this.watchlistService = watchlistService;
        this.eventRiskService = eventRiskService;
    }

    /** Result of a signal list query. */
    public record SignalListResult(List<SignalDto> items, String nextCursor) {
    }

    public SignalListResult list(SignalType type, String market, String timeframeCode,
                                 Long instrumentId, boolean watchlistOnly, SignalStatus status,
                                 boolean nearOnly, boolean activeOnly, String cursor, Long currentUserId) {
        String tfCode = null;
        if (timeframeCode != null && !timeframeCode.isBlank()) {
            try {
                tfCode = Timeframe.fromCode(timeframeCode).code();
            } catch (ApiException e) {
                throw new ApiException(ErrorCode.INVALID_FILTER,
                        "Unsupported timeframe: " + timeframeCode);
            }
        }
        String marketFilter = (market == null || market.isBlank()) ? null : market.toUpperCase();

        List<Long> instrumentIds = null;
        if (watchlistOnly) {
            instrumentIds = watchlistService.instrumentIdsForUser(currentUserId);
            if (instrumentIds.isEmpty()) {
                return new SignalListResult(List.of(), null);
            }
        }

        java.time.Instant cursorTs = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            CursorUtil.Decoded decoded = CursorUtil.decode(cursor);
            cursorTs = decoded.ts();
            cursorId = decoded.id();
        }

        List<PatternSignal> rows = signalRepository.findPage(
                type, marketFilter, tfCode, instrumentId, status, cursorTs, cursorId,
                instrumentIds, nearOnly, activeOnly, Pageable.ofSize(PAGE_SIZE + 1));

        String nextCursor = null;
        if (rows.size() > PAGE_SIZE) {
            rows = rows.subList(0, PAGE_SIZE);
            PatternSignal last = rows.get(rows.size() - 1);
            nextCursor = CursorUtil.encode(last.getDetectedAt(), last.getId());
        }

        // Batch-load instruments for the page to avoid N+1 lookups.
        List<Long> distinctInstrumentIds = rows.stream()
                .map(PatternSignal::getInstrumentId)
                .distinct()
                .toList();
        Map<Long, Instrument> instruments = instrumentRepository.findAllById(distinctInstrumentIds).stream()
                .collect(Collectors.toMap(Instrument::getId, Function.identity()));

        List<SignalDto> items = rows.stream().map(s -> toDto(s, instruments)).toList();
        return new SignalListResult(items, nextCursor);
    }

    /**
     * Top fresh signals by Pattern Score (R41). Curates the highest-quality
     * actionable candidates so the user doesn't hunt through the full feed.
     */
    public List<SignalDto> top(String market, int limit) {
        String marketFilter = (market == null || market.isBlank()) ? null : market.toUpperCase();
        int size = Math.max(1, Math.min(limit, 30));
        List<PatternSignal> rows = signalRepository.findTopByScore(
                marketFilter, Pageable.ofSize(size));
        List<Long> instrumentIds = rows.stream().map(PatternSignal::getInstrumentId).distinct().toList();
        Map<Long, Instrument> instruments = instrumentRepository.findAllById(instrumentIds).stream()
                .collect(Collectors.toMap(Instrument::getId, Function.identity()));
        return rows.stream().map(s -> toDto(s, instruments)).toList();
    }

    public SignalDetailDto detail(Long id) {
        PatternSignal signal = signalRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.SIGNAL_NOT_FOUND,
                        "Signal not found: " + id));

        Instrument instrument = instrumentRepository.findById(signal.getInstrumentId()).orElse(null);
        InstrumentRef ref = instrumentRef(signal.getInstrumentId(), instrument);

        List<SignalEvidence> ev = evidenceRepository.findBySignalIdOrderBySequenceNoAsc(signal.getId());
        List<EvidenceDto> evidence = ev.stream()
                .map(e -> new EvidenceDto(
                        e.getEvidenceType(),
                        TimeUtil.toIso(e.getCandleTime()),
                        e.getPrice() == null ? null : e.getPrice().toPlainString(),
                        e.getPayload()))
                .toList();

        InvalidationDto invalidation = null;
        if (signal.getInvalidationRule() != null) {
            invalidation = new InvalidationDto(
                    signal.getInvalidationRule(),
                    signal.getInvalidationPrice() == null
                            ? null : signal.getInvalidationPrice().toPlainString());
        }

        ChartRange chartRange = chartRangeOf(ev);

        EventRisk eventRisk = eventRiskService.assess(
                signal.getMarket(),
                instrument == null ? null : instrument.getSymbol());

        return new SignalDetailDto(
                "sig_" + signal.getId(),
                signal.getType().name(),
                signal.getMarket(),
                signal.getSubtype(),
                signal.getStatus().name(),
                ref,
                signal.getTimeframe(),
                TimeUtil.toIso(signal.getDetectedAt()),
                signal.getScore() == null ? null : signal.getScore().toPlainString(),
                signal.getCurrentPrice() == null ? null : signal.getCurrentPrice().toPlainString(),
                signal.getCTarget() == null ? null : signal.getCTarget().toPlainString(),
                evidence,
                invalidation,
                chartRange,
                signal.getAlgorithmVersion(),
                eventRisk);
    }

    private SignalDto toDto(PatternSignal s, Map<Long, Instrument> instruments) {
        return new SignalDto(
                "sig_" + s.getId(),
                s.getType().name(),
                s.getMarket(),
                s.getSubtype(),
                s.getStatus().name(),
                instrumentRef(s.getInstrumentId(), instruments.get(s.getInstrumentId())),
                s.getTimeframe(),
                TimeUtil.toIso(s.getDetectedAt()),
                s.getScore() == null ? null : s.getScore().toPlainString(),
                s.getCurrentPrice() == null ? null : s.getCurrentPrice().toPlainString(),
                s.getCTarget() == null ? null : s.getCTarget().toPlainString(),
                pivotDates(s.getId()));
    }

    /** Pivot 0/A/B candle dates from evidence, for the card summary (null when N/A). */
    private PivotDates pivotDates(Long signalId) {
        List<SignalEvidence> ev = evidenceRepository.findBySignalIdOrderBySequenceNoAsc(signalId);
        String p0 = null;
        String pa = null;
        String pb = null;
        for (SignalEvidence e : ev) {
            switch (e.getEvidenceType()) {
                case "PIVOT_0" -> p0 = TimeUtil.toIso(e.getCandleTime());
                case "PIVOT_A" -> pa = TimeUtil.toIso(e.getCandleTime());
                case "PIVOT_B" -> pb = TimeUtil.toIso(e.getCandleTime());
                default -> {
                    // TRIANGLE/IMALOL have no 0/A/B pivots — leave nulls.
                }
            }
        }
        return new PivotDates(p0, pa, pb);
    }

    private ChartRange chartRangeOf(List<SignalEvidence> ev) {
        java.time.Instant min = null;
        java.time.Instant max = null;
        for (SignalEvidence e : ev) {
            java.time.Instant t = e.getCandleTime();
            if (t == null) {
                continue;
            }
            if (min == null || t.isBefore(min)) {
                min = t;
            }
            if (max == null || t.isAfter(max)) {
                max = t;
            }
        }
        if (min == null) {
            return null;
        }
        return new ChartRange(TimeUtil.toIso(min), TimeUtil.toIso(max));
    }

    private InstrumentRef instrumentRef(Long instrumentId, Instrument instrument) {
        String symbol = instrument == null ? null : instrument.getSymbol();
        String name = instrument == null ? null : instrument.getName();
        return new InstrumentRef("ins_" + instrumentId, symbol, name);
    }
}
