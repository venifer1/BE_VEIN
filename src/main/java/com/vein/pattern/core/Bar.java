package com.vein.pattern.core;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pure, dependency-free candle view consumed by the detectors. Detectors must
 * not reference DB/network/current-time/future candles — only this list.
 */
public interface Bar {

    Instant openTime();

    BigDecimal open();

    BigDecimal high();

    BigDecimal low();

    BigDecimal close();

    BigDecimal volume();

    /** Convenience scalars for the numeric pivot/rule math (double is acceptable for detection geometry). */
    default double highD() {
        return high().doubleValue();
    }

    default double lowD() {
        return low().doubleValue();
    }

    default double closeD() {
        return close().doubleValue();
    }
}
