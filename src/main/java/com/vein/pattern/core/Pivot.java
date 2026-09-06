package com.vein.pattern.core;

/**
 * A detected local extremum: index into the candle list and its value.
 */
public record Pivot(int index, double value, Kind kind) {

    public enum Kind {
        PEAK,
        TROUGH
    }
}
