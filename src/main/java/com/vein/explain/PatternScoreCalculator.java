package com.vein.explain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PatternScoreCalculator {

    private PatternScoreCalculator() {
    }

    public record Inputs(
            BigDecimal detectorScore,
            BigDecimal volumeRatio,
            boolean priceAboveMa20,
            boolean ma5AboveMa20,
            boolean macdPositive,
            BigDecimal rsi14,
            BigDecimal averageRangePct,
            int positiveNews,
            int negativeNews) {
    }

    public record Scores(
            int completion,
            int volume,
            int trend,
            int volatility,
            int news,
            int total) {
    }

    public static Scores calculate(Inputs in, PatternScoreProperties weights) {
        int completion = scaled(in.detectorScore(), weights.completionWeight());
        int volume = volumeScore(in.volumeRatio(), weights.volumeWeight());
        int trend = trendScore(in, weights.trendWeight());
        int volatility = volatilityScore(in.averageRangePct(), weights.volatilityWeight());
        int news = newsScore(in.positiveNews(), in.negativeNews(), weights.newsWeight());
        return new Scores(completion, volume, trend, volatility, news,
                completion + volume + trend + volatility + news);
    }

    private static int scaled(BigDecimal value, int weight) {
        if (value == null) return weight / 2;
        return clamp(value.multiply(BigDecimal.valueOf(weight))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).intValue(), 0, weight);
    }

    private static int volumeScore(BigDecimal ratio, int weight) {
        if (ratio == null) return weight / 2;
        double normalized = (ratio.doubleValue() - 0.5) / 2.0;
        return clamp((int) Math.round(normalized * weight), 0, weight);
    }

    private static int trendScore(Inputs in, int weight) {
        int checks = 0;
        if (in.priceAboveMa20()) checks++;
        if (in.ma5AboveMa20()) checks++;
        if (in.macdPositive()) checks++;
        if (in.rsi14() != null
                && in.rsi14().compareTo(BigDecimal.valueOf(30)) >= 0
                && in.rsi14().compareTo(BigDecimal.valueOf(70)) <= 0) {
            checks++;
        }
        return (int) Math.round(weight * checks / 4.0);
    }

    private static int volatilityScore(BigDecimal rangePct, int weight) {
        if (rangePct == null) return weight / 2;
        double value = rangePct.doubleValue();
        if (value >= 1.0 && value <= 6.0) return weight;
        if ((value >= 0.5 && value < 1.0) || (value > 6.0 && value <= 10.0)) {
            return (int) Math.round(weight * 0.6);
        }
        return (int) Math.round(weight * 0.2);
    }

    private static int newsScore(int positive, int negative, int weight) {
        double neutral = weight / 2.0;
        double step = weight / 4.0;
        return clamp((int) Math.round(neutral + (positive - negative) * step), 0, weight);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
