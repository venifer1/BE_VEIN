package com.vein.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.vein.pattern.abc.AbcDetector;
import com.vein.pattern.abc.AbcParams;
import com.vein.pattern.abc.AbcPattern;

/**
 * ABC golden test: runs {@link AbcDetector} on every fixture's input_candles and
 * asserts against expected.abc within tolerance (pivot index exact, price_rel,
 * score ±). The provided fixture expects 0 patterns.
 */
class AbcGoldenTest {

    static Stream<Path> fixtures() throws IOException {
        return GoldenFixture.fixtureFiles().stream();
    }

    @ParameterizedTest(name = "ABC golden [{0}]")
    @MethodSource("fixtures")
    void abcMatchesGolden(Path file) throws IOException {
        GoldenFixture.Loaded fx = GoldenFixture.load(file);
        Assumptions.assumeFalse(fx.candles().isEmpty(), "no candles in fixture");

        AbcParams params = paramsFrom(fx.root());
        List<AbcPattern> actual = new AbcDetector(params).detect(fx.candles());

        JsonNode expected = fx.root().path("expected").path("abc");
        JsonNode tol = fx.root().path("tolerance");
        double priceRel = tol.path("price_rel").asDouble(0.0005);
        double scoreTol = tol.path("score").asDouble(1.0);

        assertThat(actual)
                .as("ABC pattern count for %s", fx.fixtureId())
                .hasSize(expected.size());

        for (int i = 0; i < expected.size(); i++) {
            JsonNode exp = expected.get(i);
            AbcPattern act = actual.get(i);
            assertPivots(exp, act, fx.fixtureId(), i);
            assertPrice(exp, "p0_val", act.p0Val(), priceRel, fx.fixtureId());
            assertPrice(exp, "pA_val", act.pAVal(), priceRel, fx.fixtureId());
            assertPrice(exp, "pB_val", act.pBVal(), priceRel, fx.fixtureId());
            assertPrice(exp, "c_100", act.c100(), priceRel, fx.fixtureId());
            if (exp.has("score")) {
                assertThat(act.score())
                        .as("ABC score %s[%d]", fx.fixtureId(), i)
                        .isCloseTo(exp.path("score").asDouble(), org.assertj.core.data.Offset.offset(scoreTol));
            }
        }
    }

    private void assertPivots(JsonNode exp, AbcPattern act, String fixtureId, int i) {
        if (exp.has("pivots_idx") && exp.path("pivots_idx").isArray()) {
            JsonNode arr = exp.path("pivots_idx");
            int[] actualIdx = {act.idx0(), act.idxA(), act.idxB()};
            for (int k = 0; k < arr.size() && k < actualIdx.length; k++) {
                assertThat(actualIdx[k])
                        .as("ABC pivot idx %s[%d][%d] (exact)", fixtureId, i, k)
                        .isEqualTo(arr.get(k).asInt());
            }
        }
    }

    private void assertPrice(JsonNode exp, String field, double actual, double priceRel, String fixtureId) {
        if (!exp.has(field)) {
            return;
        }
        double expected = exp.path(field).asDouble();
        double allowed = Math.abs(expected) * priceRel;
        assertThat(Math.abs(actual - expected))
                .as("ABC %s %s rel-err <= %s", field, fixtureId, priceRel)
                .isLessThanOrEqualTo(allowed);
    }

    private AbcParams paramsFrom(JsonNode root) {
        JsonNode p = root.path("algorithm_params").path("abc");
        if (p.isMissingNode()) {
            return AbcParams.defaults();
        }
        return new AbcParams(
                p.path("LOCAL_WIN").asInt(5),
                p.path("SEARCH_WINDOW").asInt(100),
                p.path("MIN_A_DROP_PCT").asDouble(0.20),
                p.path("MIN_B_RETRACE_PCT").asDouble(0.236),
                p.path("MAX_B_RETRACE_PCT").asDouble(0.886),
                p.path("MIN_0_PROMINENCE").asDouble(0.10),
                p.path("STRICT_WAVE").asBoolean(true),
                p.path("MAX_PATTERNS").asInt(AbcParams.DEFAULT_MAX_PATTERNS));
    }
}
