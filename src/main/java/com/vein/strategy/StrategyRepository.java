package com.vein.strategy;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyRepository extends JpaRepository<Strategy, Long> {

    List<Strategy> findByUserIdOrderByCreatedAtDesc(Long userId);
}
