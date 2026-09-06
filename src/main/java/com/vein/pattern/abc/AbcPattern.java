package com.vein.pattern.abc;

/**
 * One detected ABC pattern. Indices are into the candle list passed to the
 * detector (pivot_index exact comparison in golden tests). Prices are doubles
 * for geometry; the persistence layer converts to BigDecimal.
 */
public record AbcPattern(
        int idx0,
        int idxA,
        int idxB,
        double p0Val,
        double pAVal,
        double pBVal,
        double c100,
        double score) {
}
