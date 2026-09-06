package com.vein.explain;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vein.pattern-score")
public record PatternScoreProperties(
        int completionWeight,
        int volumeWeight,
        int trendWeight,
        int volatilityWeight,
        int newsWeight) {
}
