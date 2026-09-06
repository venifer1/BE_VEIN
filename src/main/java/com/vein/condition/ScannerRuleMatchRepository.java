package com.vein.condition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScannerRuleMatchRepository extends JpaRepository<ScannerRuleMatch, Long> {

    List<ScannerRuleMatch> findByRuleIdAndActiveTrue(Long ruleId);

    List<ScannerRuleMatch> findByRuleIdAndInstrumentIdNotInAndActiveTrue(
            Long ruleId, Collection<Long> instrumentIds);

    Optional<ScannerRuleMatch> findByRuleIdAndInstrumentId(Long ruleId, Long instrumentId);

    long countByActiveTrue();
}
