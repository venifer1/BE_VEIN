package com.vein.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vein.pattern.abc.AbcDetector;
import com.vein.pattern.abc.AbcParams;
import com.vein.pattern.abc.AbcPattern;
import com.vein.pattern.core.Bar;

class AbcDetectorTest {

    private static final AbcParams PARAMS = new AbcParams(
            1, 100, 0.20, 0.236, 0.886, 0.0, true, 3);

    @Test
    void allowsShortABWhenBIsHighestAfterA() {
        List<AbcPattern> patterns = new AbcDetector(PARAMS).detect(bars(
                new double[] {100, 105, 130, 110, 92, 100, 110, 105, 108, 107},
                new double[] {99, 100, 125, 100, 90, 95, 100, 100, 103, 102}));

        assertThat(patterns).hasSize(1);
        assertThat(patterns.get(0).idxA()).isEqualTo(4);
        assertThat(patterns.get(0).idxB()).isEqualTo(6);
    }

    @Test
    void rejectsBWhenLaterHighAfterAExceedsIt() {
        List<AbcPattern> patterns = new AbcDetector(PARAMS).detect(bars(
                new double[] {100, 105, 130, 110, 92, 100, 110, 105, 108, 115},
                new double[] {99, 100, 125, 100, 90, 95, 100, 100, 103, 110}));

        assertThat(patterns).isEmpty();
    }

    private static List<Bar> bars(double[] highs, double[] lows) {
        List<Bar> out = new ArrayList<>();
        for (int i = 0; i < highs.length; i++) {
            double close = (highs[i] + lows[i]) / 2.0;
            out.add(SyntheticBars.bar(i, close, highs[i], lows[i], close, 100));
        }
        return out;
    }
}
