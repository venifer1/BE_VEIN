package com.vein.market.kimchi;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface KimchiPremiumRepository extends JpaRepository<KimchiPremium, Long> {

    Optional<KimchiPremium> findTopByOrderByCollectedAtDesc();

    /** The most recent snapshot row per instrument (latest batch). */
    @Query("""
            select k from KimchiPremium k
            where k.collectedAt = (select max(k2.collectedAt) from KimchiPremium k2)
            """)
    List<KimchiPremium> findLatestBatch();

    List<KimchiPremium> findByCollectedAt(Instant collectedAt);
}
