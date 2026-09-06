package com.vein.market.candle;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface CandleRepository extends JpaRepository<Candle, CandleId> {

    List<Candle> findByIdInstrumentIdAndIdTimeframeOrderByIdOpenTimeAsc(
            Long instrumentId, String timeframe);

    Optional<Candle> findTopByIdInstrumentIdAndIdTimeframeOrderByIdOpenTimeDesc(
            Long instrumentId, String timeframe);

    /**
     * Newest candle for an instrument across all stored timeframes/providers,
     * by open_time. Used to surface a "latest price" without knowing which
     * timeframes are stored (watchlist enrichment).
     */
    Optional<Candle> findTopByIdInstrumentIdOrderByIdOpenTimeDesc(Long instrumentId);

    /**
     * Latest two candles for an instrument+timeframe, newest first. Used by movers
     * to compute change_rate from the last two daily closes (equities movers).
     */
    List<Candle> findTop2ByIdInstrumentIdAndIdTimeframeOrderByIdOpenTimeDesc(
            Long instrumentId, String timeframe);

    /**
     * Candles for an instrument+timeframe within [from, to] (inclusive),
     * ascending by open time. Null bounds are treated as open-ended.
     */
    @Query("""
            select c from Candle c
            where c.id.instrumentId = :instrumentId
              and c.id.timeframe = :timeframe
              and (cast(:from as Instant) is null or c.id.openTime >= :from)
              and (cast(:to as Instant) is null or c.id.openTime <= :to)
            order by c.id.openTime asc
            """)
    List<Candle> findRangeAsc(@Param("instrumentId") Long instrumentId,
                              @Param("timeframe") String timeframe,
                              @Param("from") Instant from,
                              @Param("to") Instant to);

    /**
     * Latest candles within an optional [from, to] window, newest first, limited
     * by {@code pageable}. Caller should reverse to ascending for presentation.
     */
    @Query("""
            select c from Candle c
            where c.id.instrumentId = :instrumentId
              and c.id.timeframe = :timeframe
              and (cast(:from as Instant) is null or c.id.openTime >= :from)
              and (cast(:to as Instant) is null or c.id.openTime <= :to)
            order by c.id.openTime desc
            """)
    List<Candle> findLatest(@Param("instrumentId") Long instrumentId,
                            @Param("timeframe") String timeframe,
                            @Param("from") Instant from,
                            @Param("to") Instant to,
                            Pageable pageable);

    /**
     * First candle at/after {@code at} for an instrument+timeframe, ascending by
     * open time. Used to resolve price_at_horizon (signal performance §31).
     */
    @Query("""
            select c from Candle c
            where c.id.instrumentId = :instrumentId
              and c.id.timeframe = :timeframe
              and c.id.openTime >= :at
            order by c.id.openTime asc
            """)
    List<Candle> findFromAsc(@Param("instrumentId") Long instrumentId,
                             @Param("timeframe") String timeframe,
                             @Param("at") Instant at,
                             Pageable pageable);
}
