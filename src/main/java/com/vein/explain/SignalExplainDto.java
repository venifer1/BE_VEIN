package com.vein.explain;

import java.util.List;

public record SignalExplainDto(
        String signalId,
        int patternScore,
        String riskGuard,
        List<ScoreFactor> factors,
        List<String> reasons,
        List<String> risks,
        List<String> nextChecks,
        Confidence confidence,
        String templateVersion) {

    public record ScoreFactor(String key, String label, int score, int maxScore, String detail) {
    }

    public record Confidence(String grade, long sampleSize, String hitRate,
                             String avgReturnPct, String horizon) {
    }
}
