package com.vein.pattern.imalol;

import java.util.List;

/**
 * One detected IMALOL (이말올) signal for an instrument+timeframe. The legacy UI
 * merges every matched 2-candle box into a single table row per instrument+TF;
 * this record mirrors that: a list of {@link MatchBox}es plus the Bollinger band
 * (20, 2.0) sampled at the anchor candle (the most recent box's 2nd/cur candle).
 *
 * <p>anchorIdx is the index of the anchor candle (the cur of the most recent box)
 * — used as the signal's anchor_candle_time. projectedClose = anchor cur close
 * (legacy c100). currentPrice = last close of the series.
 */
public record ImalolPattern(
        int anchorIdx,
        double projectedClose,
        double currentPrice,
        double bollUpper,
        double bollMid,
        double bollLower,
        List<MatchBox> boxes,
        double score) {

    /**
     * A matched 2-candle box: prev (1st) and cur (2nd) candle indices plus their
     * high/low bounds and the cur close (the box's projected price).
     */
    public record MatchBox(
            int prevIdx,
            int curIdx,
            double prevHigh,
            double prevLow,
            double curHigh,
            double curLow,
            double curClose) {
    }
}
