package com.vein.pattern.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Local-extremum detection (부록 E-1).
 *
 * <pre>
 * findPeaks(values, win):   i is a peak if values[i] &gt;= every neighbor in [i-win, i+win]
 * findTroughs(values, win): i is a trough if values[i] &lt;= every neighbor in [i-win, i+win]
 * win = LOCAL_WIN = 5
 * </pre>
 *
 * Edge indices that do not have a full window on both sides are not pivots
 * (a peak/trough must have win neighbors on each side), matching the legacy
 * Python which scans the interior [win, n-win).
 */
public final class PivotDetector {

    public static final int LOCAL_WIN = 5;

    private PivotDetector() {
    }

    public static List<Integer> findPeaks(double[] values, int win) {
        List<Integer> out = new ArrayList<>();
        int n = values.length;
        for (int i = win; i < n - win; i++) {
            boolean isPeak = true;
            for (int j = i - win; j <= i + win; j++) {
                if (j == i) {
                    continue;
                }
                if (values[i] < values[j]) {
                    isPeak = false;
                    break;
                }
            }
            if (isPeak) {
                out.add(i);
            }
        }
        return out;
    }

    public static List<Integer> findTroughs(double[] values, int win) {
        List<Integer> out = new ArrayList<>();
        int n = values.length;
        for (int i = win; i < n - win; i++) {
            boolean isTrough = true;
            for (int j = i - win; j <= i + win; j++) {
                if (j == i) {
                    continue;
                }
                if (values[i] > values[j]) {
                    isTrough = false;
                    break;
                }
            }
            if (isTrough) {
                out.add(i);
            }
        }
        return out;
    }

    public static double[] highs(List<? extends Bar> bars) {
        double[] a = new double[bars.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = bars.get(i).highD();
        }
        return a;
    }

    public static double[] lows(List<? extends Bar> bars) {
        double[] a = new double[bars.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = bars.get(i).lowD();
        }
        return a;
    }
}
