package com.vein.supply;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupplySnapshotRepository extends JpaRepository<SupplySnapshot, Long> {

    Optional<SupplySnapshot> findTopByOrderByCollectedAtDesc();

    /** All rows from the latest batch (ordered by rank). */
    @Query("""
            select s from SupplySnapshot s
            where s.collectedAt = (select max(s2.collectedAt) from SupplySnapshot s2)
            order by s.rank asc nulls last
            """)
    List<SupplySnapshot> findLatestBatch();

    void deleteByCollectedAtBefore(Instant cutoff);
}
