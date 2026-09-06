package com.vein.strategy;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyRunRepository extends JpaRepository<StrategyRun, Long> {

    /** History for one strategy, newest run first; cap via {@link Pageable}. */
    List<StrategyRun> findByStrategyIdOrderByRunAtDesc(Long strategyId, Pageable pageable);
}
