package com.vein.pattern.abc;

/**
 * ABC detector parameters (부록 E-2 / 표 18). Defaults are the legacy Python
 * config values; the golden fixture supplies the same values for verification.
 */
public record AbcParams(
        int localWin,
        int searchWindow,
        double minADropPct,
        double minBRetracePct,
        double maxBRetracePct,
        double min0Prominence,
        boolean strictWave,
        int maxPatterns) {

    public static final int DEFAULT_MAX_PATTERNS = 10;

    public static AbcParams defaults() {
        return new AbcParams(5, 100, 0.20, 0.236, 0.886, 0.10, true, DEFAULT_MAX_PATTERNS);
    }
}
