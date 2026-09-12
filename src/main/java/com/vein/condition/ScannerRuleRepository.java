package com.vein.condition;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScannerRuleRepository extends JpaRepository<ScannerRule, Long> {
    List<ScannerRule> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserId(Long userId);

    List<ScannerRule> findByEnabledTrueOrderByCreatedAtAsc();

    long countByEnabledTrue();
}
