package com.vein.signal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatternSignalRepository extends JpaRepository<PatternSignal, Long> {

    Optional<PatternSignal> findBySignalKey(String signalKey);

    boolean existsBySignalKey(String signalKey);

    List<PatternSignal> findByStatusIn(List<SignalStatus> statuses);

    /** Most recent signal (any type/status) for an instrument, newest by detected_at. */
    Optional<PatternSignal> findTopByInstrumentIdOrderByDetectedAtDesc(Long instrumentId);

    /**
     * Cursor list, newest-first by (detected_at, id), with optional filters.
     * Cursor is (detected_at, id): rows strictly older than the cursor.
     */
    @Query("""
            select s from PatternSignal s
            where (:type is null or s.type = :type)
              and (:market is null or s.market = :market)
              and (:timeframe is null or s.timeframe = :timeframe)
              and (:instrumentId is null or s.instrumentId = :instrumentId)
              and (:status is null or s.status = :status)
              and ( :activeOnly = false
                    or ( s.status in (com.vein.signal.SignalStatus.DETECTED,
                                      com.vein.signal.SignalStatus.NEAR_COMPLETION)
                         and (s.expiresAt is null or s.expiresAt > :now) ) )
              and ( cast(:cursorTs as Instant) is null
                    or s.detectedAt < :cursorTs
                    or (s.detectedAt = :cursorTs and s.id < :cursorId) )
              and ( :instrumentIds is null or s.instrumentId in :instrumentIds )
              and ( :nearOnly = false
                    or ( s.type in (com.vein.signal.SignalType.ABC, com.vein.signal.SignalType.TOP)
                         and s.cTarget is not null and s.currentPrice is not null
                         and s.currentPrice <> 0
                         and abs(s.cTarget - s.currentPrice) / abs(s.currentPrice) <= 0.05 ) )
            order by s.detectedAt desc, s.id desc
            """)
    List<PatternSignal> findPage(@Param("type") SignalType type,
                                 @Param("market") String market,
                                 @Param("timeframe") String timeframe,
                                 @Param("instrumentId") Long instrumentId,
                                 @Param("status") SignalStatus status,
                                 @Param("cursorTs") Instant cursorTs,
                                 @Param("cursorId") Long cursorId,
                                 @Param("instrumentIds") List<Long> instrumentIds,
                                 @Param("nearOnly") boolean nearOnly,
                                 @Param("activeOnly") boolean activeOnly,
                                 @Param("now") Instant now,
                                 Pageable pageable);

    /**
     * Top fresh signals by <b>composite Pattern Score</b> (R41 큐레이션, R90 정렬 개선).
     * Only actionable statuses (DETECTED / NEAR_COMPLETION), non-null score, <b>and not past
     * expiry</b> (R82: 스케줄러 전이 지연/게이트로 상태가 아직 active여도 시간상 만료된 신호는
     * 후보에서 제외), optionally scoped by market.
     *
     * <p>정렬 키는 {@code coalesce(pattern_score, score)} — 완성도·거래량·추세·변동성·뉴스를
     * 합산한 종합 점수(SignalPatternScoreService가 영속화, R90)가 있으면 그걸로, 아직 미계산이면
     * 구조 점수로 폴백(하위호환). 동점은 최신순.
     */
    @Query("""
            select s from PatternSignal s
            where s.status in (com.vein.signal.SignalStatus.DETECTED, com.vein.signal.SignalStatus.NEAR_COMPLETION)
              and s.score is not null
              and (s.expiresAt is null or s.expiresAt > :now)
              and (:market is null or s.market = :market)
            order by coalesce(s.patternScore, s.score) desc, s.detectedAt desc, s.id desc
            """)
    List<PatternSignal> findTopByScore(@Param("market") String market,
                                       @Param("now") Instant now, Pageable pageable);

    /**
     * Backtest universe (기획서 §11): signals of a given type, optionally scoped by
     * market/timeframe, detected at/after {@code since}, oldest-first so trades replay
     * in chronological order. {@code since} is required (non-null) so no Instant cast
     * is needed; market/timeframe are nullable String filters (PG-safe as-is).
     */
    @Query("""
            select s from PatternSignal s
            where s.type = :type
              and (:market is null or s.market = :market)
              and (:timeframe is null or s.timeframe = :timeframe)
              and s.detectedAt >= :since
            order by s.detectedAt asc, s.id asc
            """)
    List<PatternSignal> findForBacktest(@Param("type") SignalType type,
                                        @Param("market") String market,
                                        @Param("timeframe") String timeframe,
                                        @Param("since") Instant since);
}
