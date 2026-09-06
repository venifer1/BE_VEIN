package com.vein.pattern.imalol;

/**
 * IMALOL detector parameters (legacy {@code ui/tab_imalol.py} constants).
 * <ul>
 *   <li>maPeriod = 20 (MA20 exclusion),</li>
 *   <li>bollPeriod = 20, bollK = 2.0 (lower-band touch),</li>
 *   <li>recentRiseBars = 3 (exclude if a rising close in the prior 3 bars),</li>
 *   <li>recencyBars = 2 (only emit when the box just appeared: its cur candle
 *       is within this many bars of the latest candle).</li>
 * </ul>
 */
public record ImalolParams(int maPeriod, int bollPeriod, double bollK, int recentRiseBars,
                           int recencyBars) {

    public static ImalolParams defaults() {
        return new ImalolParams(20, 20, 2.0, 3, 2);
    }
}
