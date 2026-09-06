package com.vein.signal;

import java.time.Duration;

/**
 * Fixed wall-clock horizons over which signal performance is measured after
 * detection (기획서 §31). These are durations, independent of the signal's
 * candle timeframe.
 */
public enum PerformanceHorizon {
    H1("1h", Duration.ofHours(1)),
    H4("4h", Duration.ofHours(4)),
    D1("1d", Duration.ofDays(1)),
    D3("3d", Duration.ofDays(3)),
    D7("7d", Duration.ofDays(7));

    private final String code;
    private final Duration duration;

    PerformanceHorizon(String code, Duration duration) {
        this.code = code;
        this.duration = duration;
    }

    public String code() {
        return code;
    }

    public Duration duration() {
        return duration;
    }
}
