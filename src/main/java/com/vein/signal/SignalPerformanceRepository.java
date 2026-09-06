package com.vein.signal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignalPerformanceRepository extends JpaRepository<SignalPerformance, Long> {

    List<SignalPerformance> findBySignalIdOrderByHorizonAsc(Long signalId);

    Optional<SignalPerformance> findBySignalIdAndHorizon(Long signalId, String horizon);

    /**
     * Flat performance rows for one horizon joined to their signal's
     * (type, market, timeframe), for the summary endpoint. Filters are optional
     * (null = no filter). Grouping, hit-rate, averages and median are computed in
     * {@link SignalPerformanceService} so a null {@code market} group is handled
     * cleanly (avoids PG null-equality pitfalls in JPQL group keys).
     */
    @Query("""
            select s.type as type, s.market as market, s.timeframe as timeframe,
                   p.returnPct as returnPct, p.mfePct as mfePct, p.maePct as maePct
            from SignalPerformance p
              join PatternSignal s on s.id = p.signalId
            where p.horizon = :horizon
              and p.returnPct is not null
              and (:type is null or s.type = :type)
              and (:market is null or s.market = :market)
              and (:timeframe is null or s.timeframe = :timeframe)
            """)
    List<SummaryRow> summaryRows(@Param("horizon") String horizon,
                                 @Param("type") SignalType type,
                                 @Param("market") String market,
                                 @Param("timeframe") String timeframe);

    /** One performance row with its signal grouping fields. */
    interface SummaryRow {
        SignalType getType();

        String getMarket();

        String getTimeframe();

        BigDecimal getReturnPct();

        BigDecimal getMfePct();

        BigDecimal getMaePct();
    }
}
