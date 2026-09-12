package com.vein.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vein.onboarding.OnboardingDto.Status;
import com.vein.onboarding.OnboardingDto.Step;

/**
 * 순수 단위(DB 불필요). 스텝 순서·진행률·all_done·dismissed 전파를 고정한다.
 */
class OnboardingStepsTest {

    @Test
    void fixedStepOrderAndLabels() {
        Status s = OnboardingSteps.build(false, false, false, false, false);
        assertThat(s.steps()).extracting(Step::key)
                .containsExactly("WATCHLIST", "ALERT", "PAPER", "SCANNER");
        assertThat(s.steps().get(0).label()).isEqualTo("관심종목 추가");
        assertThat(s.steps().get(0).href()).isEqualTo("/");
        assertThat(s.total()).isEqualTo(4);
    }

    @Test
    void noneDone() {
        Status s = OnboardingSteps.build(false, false, false, false, false);
        assertThat(s.completed()).isZero();
        assertThat(s.allDone()).isFalse();
        assertThat(s.dismissed()).isFalse();
        assertThat(s.steps()).allSatisfy(step -> assertThat(step.done()).isFalse());
    }

    @Test
    void partialCompletionCountsDoneSteps() {
        // watchlist + paper done → 2/4
        Status s = OnboardingSteps.build(true, false, true, false, false);
        assertThat(s.completed()).isEqualTo(2);
        assertThat(s.allDone()).isFalse();
        assertThat(s.steps().get(0).done()).isTrue();  // WATCHLIST
        assertThat(s.steps().get(1).done()).isFalse(); // ALERT
        assertThat(s.steps().get(2).done()).isTrue();  // PAPER
        assertThat(s.steps().get(3).done()).isFalse(); // SCANNER
    }

    @Test
    void allDoneWhenEveryStepComplete() {
        Status s = OnboardingSteps.build(true, true, true, true, false);
        assertThat(s.completed()).isEqualTo(4);
        assertThat(s.allDone()).isTrue();
    }

    @Test
    void dismissedFlagIsIndependentOfProgress() {
        Status s = OnboardingSteps.build(false, false, false, false, true);
        assertThat(s.dismissed()).isTrue();
        assertThat(s.allDone()).isFalse();
        assertThat(s.completed()).isZero();
    }
}
