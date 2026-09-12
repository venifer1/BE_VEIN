package com.vein.explain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * R79: Explain 피드백 요청의 {@code helpful}은 명시 필수다. primitive boolean이던 시절엔
 * 빈 바디 {@code {}}가 {@code helpful=false}로 채워져 "아쉬워요"가 조용히 기록되며 유용률(R22)을
 * 오염시켰다. {@code @NotNull Boolean}으로 바꿔 누락(null)은 제약 위반 → 컨트롤러 @Valid가 400을 낸다.
 * 순수 단위(스프링 컨텍스트/DB 불필요).
 */
class ExplainFeedbackRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void nullHelpful_violatesNotNull() {
        var request = new ExplainFeedbackService.Request(null, "UNCLEAR");
        Set<ConstraintViolation<ExplainFeedbackService.Request>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("helpful");
    }

    @Test
    void explicitHelpful_isValid() {
        assertThat(validator.validate(new ExplainFeedbackService.Request(true, null))).isEmpty();
        assertThat(validator.validate(new ExplainFeedbackService.Request(false, "MISSING_RISK"))).isEmpty();
    }
}
