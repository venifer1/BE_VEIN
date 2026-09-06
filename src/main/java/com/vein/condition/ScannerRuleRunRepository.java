package com.vein.condition;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScannerRuleRunRepository extends JpaRepository<ScannerRuleRun, Long> {

    List<ScannerRuleRun> findByRuleIdOrderByCreatedAtDesc(Long ruleId, Pageable pageable);

    @Query("""
            SELECT r FROM ScannerRuleRun r
            WHERE r.createdAt >= :since
            ORDER BY r.createdAt DESC
            """)
    List<ScannerRuleRun> findRecent(@Param("since") Instant since, Pageable pageable);

    long countByCreatedAtAfter(Instant since);
}
