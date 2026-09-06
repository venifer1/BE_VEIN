package com.vein.explain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExplainFeedbackRepository extends JpaRepository<ExplainFeedback, Long> {
    Optional<ExplainFeedback> findByUserIdAndSignalId(Long userId, Long signalId);

    long countBySignalId(Long signalId);

    long countBySignalIdAndHelpfulTrue(Long signalId);
}
