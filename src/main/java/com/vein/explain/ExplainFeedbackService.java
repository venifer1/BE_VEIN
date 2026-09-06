package com.vein.explain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.vein.common.ApiException;
import com.vein.common.ErrorCode;
import com.vein.signal.PatternSignalRepository;

@Service
public class ExplainFeedbackService {

    private final ExplainFeedbackRepository repository;
    private final PatternSignalRepository signalRepository;

    public ExplainFeedbackService(ExplainFeedbackRepository repository,
                                  PatternSignalRepository signalRepository) {
        this.repository = repository;
        this.signalRepository = signalRepository;
    }

    public record Request(boolean helpful, String reason) {
    }

    public record Summary(long totalCount, long helpfulCount, String helpfulRate,
                          Boolean myHelpful, String myReason) {
    }

    @Transactional
    public Summary submit(Long userId, Long signalId, Request request) {
        if (!signalRepository.existsById(signalId)) {
            throw new ApiException(ErrorCode.SIGNAL_NOT_FOUND);
        }
        String reason = normalizeReason(request.reason());
        ExplainFeedback feedback = repository.findByUserIdAndSignalId(userId, signalId)
                .orElseGet(() -> ExplainFeedback.create(
                        userId, signalId, request.helpful(), reason));
        feedback.update(request.helpful(), reason);
        repository.save(feedback);
        return summary(userId, signalId);
    }

    @Transactional(readOnly = true)
    public Summary summary(Long userId, Long signalId) {
        if (!signalRepository.existsById(signalId)) {
            throw new ApiException(ErrorCode.SIGNAL_NOT_FOUND);
        }
        long total = repository.countBySignalId(signalId);
        long helpful = repository.countBySignalIdAndHelpfulTrue(signalId);
        String rate = total == 0 ? null : BigDecimal.valueOf(helpful)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                .toPlainString();
        ExplainFeedback mine = repository.findByUserIdAndSignalId(userId, signalId).orElse(null);
        return new Summary(total, helpful, rate,
                mine == null ? null : mine.isHelpful(),
                mine == null ? null : mine.getReason());
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) return null;
        String normalized = reason.trim().toUpperCase();
        return switch (normalized) {
            case "UNCLEAR", "INACCURATE", "MISSING_RISK", "TOO_COMPLEX", "OTHER" -> normalized;
            default -> throw new ApiException(ErrorCode.INVALID_QUERY, "Unsupported feedback reason");
        };
    }
}
