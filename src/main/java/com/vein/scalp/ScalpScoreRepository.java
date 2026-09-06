package com.vein.scalp;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScalpScoreRepository extends JpaRepository<ScalpScore, Long> {

    Optional<ScalpScore> findTopByOrderByCollectedAtDesc();

    /** Latest batch ordered by score desc (the current ranking). */
    @Query("""
            select s from ScalpScore s
            where s.collectedAt = (select max(s2.collectedAt) from ScalpScore s2)
            order by s.scalpScore desc nulls last, s.tps desc nulls last
            """)
    List<ScalpScore> findLatestBatch();

    /** Latest row for one symbol (detail view). */
    @Query("""
            select s from ScalpScore s
            where s.symbol = :symbol
            order by s.collectedAt desc
            limit 1
            """)
    Optional<ScalpScore> findLatestBySymbol(@Param("symbol") String symbol);

    void deleteByCollectedAtBefore(Instant cutoff);
}
