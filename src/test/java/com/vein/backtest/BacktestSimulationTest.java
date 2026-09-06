package com.vein.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vein.backtest.BacktestService.Outcome;
import com.vein.backtest.BacktestService.Sim;
import com.vein.pattern.core.Bar;

/**
 * Pure unit tests for the backtest trade simulation on synthetic candle series.
 * No Spring context, no DB.
 */
class BacktestSimulationTest {

    private static final BigDecimal NO_FEE = BigDecimal.ZERO;
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    /** Minimal synthetic OHLC bar. */
    private record TestBar(Instant openTime, BigDecimal open, BigDecimal high,
                           BigDecimal low, BigDecimal close, BigDecimal volume) implements Bar {
    }

    private static Bar bar(int idx, double high, double low, double close) {
        return new TestBar(T0.plus(Duration.ofHours(idx)),
                BigDecimal.valueOf(close), BigDecimal.valueOf(high),
                BigDecimal.valueOf(low), BigDecimal.valueOf(close), BigDecimal.ONE);
    }

    @Test
    void exitsAtTargetWhenHighReachesIt() {
        BigDecimal entry = BigDecimal.valueOf(100);
        // bar 1: range 99-104 (no hit at +5%=105); bar 2: high 106 hits target.
        List<Bar> candles = List.of(bar(1, 104, 99, 103), bar(2, 106, 102, 105));
        Sim sim = BacktestService.simulate(entry, candles,
                BigDecimal.valueOf(5), BigDecimal.valueOf(5), NO_FEE);

        assertThat(sim.outcome()).isEqualTo(Outcome.WIN);
        assertThat(sim.exitPrice()).isEqualByComparingTo("105"); // entry * 1.05
        assertThat(sim.returnPct()).isEqualByComparingTo("5.0000");
        assertThat(sim.holdBars()).isEqualTo(2);
    }

    @Test
    void exitsAtStopWhenLowReachesIt() {
        BigDecimal entry = BigDecimal.valueOf(100);
        // bar 1: low 94 hits -5% stop (=95).
        List<Bar> candles = List.of(bar(1, 101, 94, 96));
        Sim sim = BacktestService.simulate(entry, candles,
                BigDecimal.valueOf(5), BigDecimal.valueOf(5), NO_FEE);

        assertThat(sim.outcome()).isEqualTo(Outcome.LOSS);
        assertThat(sim.exitPrice()).isEqualByComparingTo("95");
        assertThat(sim.returnPct()).isEqualByComparingTo("-5.0000");
        assertThat(sim.holdBars()).isEqualTo(1);
    }

    @Test
    void countsAsLossWhenBothHitInSameBar() {
        BigDecimal entry = BigDecimal.valueOf(100);
        // bar reaches both +5% target (high 106) and -5% stop (low 94) -> conservative LOSS.
        List<Bar> candles = List.of(bar(1, 106, 94, 100));
        Sim sim = BacktestService.simulate(entry, candles,
                BigDecimal.valueOf(5), BigDecimal.valueOf(5), NO_FEE);

        assertThat(sim.outcome()).isEqualTo(Outcome.LOSS);
        assertThat(sim.exitPrice()).isEqualByComparingTo("95");
    }

    @Test
    void exitsAtHorizonCloseWhenNothingHit() {
        BigDecimal entry = BigDecimal.valueOf(100);
        // never reaches +5% or -5%; last close = 102 -> TIME exit.
        List<Bar> candles = List.of(bar(1, 103, 98, 101), bar(2, 104, 99, 102));
        Sim sim = BacktestService.simulate(entry, candles,
                BigDecimal.valueOf(5), BigDecimal.valueOf(5), NO_FEE);

        assertThat(sim.outcome()).isEqualTo(Outcome.TIME);
        assertThat(sim.exitPrice()).isEqualByComparingTo("102");
        assertThat(sim.returnPct()).isEqualByComparingTo("2.0000");
        assertThat(sim.holdBars()).isEqualTo(2);
    }

    @Test
    void subtractsRoundTripFeeFromReturn() {
        BigDecimal entry = BigDecimal.valueOf(100);
        List<Bar> candles = List.of(bar(1, 106, 102, 105));
        Sim sim = BacktestService.simulate(entry, candles,
                BigDecimal.valueOf(5), BigDecimal.valueOf(5), new BigDecimal("0.1"));

        // gross +5% minus 0.1% fee = 4.9%
        assertThat(sim.returnPct()).isEqualByComparingTo("4.9000");
    }

    @Test
    void parsesHorizonUnits() {
        assertThat(BacktestService.parseHorizon("1d")).isEqualTo(Duration.ofDays(1));
        assertThat(BacktestService.parseHorizon("4h")).isEqualTo(Duration.ofHours(4));
        assertThat(BacktestService.parseHorizon("2w")).isEqualTo(Duration.ofDays(14));
        assertThat(BacktestService.parseHorizon("12")).isEqualTo(Duration.ofHours(12));
    }
}
