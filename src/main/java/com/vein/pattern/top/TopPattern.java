package com.vein.pattern.top;

/**
 * One detected TOP (고점판독) pattern. Mirror of {@code AbcPattern} in the
 * non-inverted price domain: 0 is a trough (the local bottom that starts the
 * rise), A a peak, B a pullback trough, c_100 the projected C target above.
 * Indices are into the candle list passed to the detector.
 */
public record TopPattern(
        int idx0,
        int idxA,
        int idxB,
        double p0Val,
        double pAVal,
        double pBVal,
        double c100,
        double score) {
}
