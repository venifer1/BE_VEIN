package com.vein.funding;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FundingArbSnapshotRepository extends JpaRepository<FundingArbSnapshot, Long> {

    Optional<FundingArbSnapshot> findTopByOrderByCollectedAtDesc();

    /** All rows from the latest batch. */
    @Query("""
            select f from FundingArbSnapshot f
            where f.collectedAt = (select max(f2.collectedAt) from FundingArbSnapshot f2)
            """)
    List<FundingArbSnapshot> findLatestBatch();

    void deleteByCollectedAtBefore(Instant cutoff);
}
