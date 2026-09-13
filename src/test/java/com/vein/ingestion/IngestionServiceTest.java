package com.vein.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.vein.common.ApiException;
import com.vein.common.ErrorCode;

/**
 * 수집 실패 오류코드 분류 순수 로직 회귀 보호(R182). ApiException은 ErrorCode 이름,
 * 그 외 RuntimeException은 클래스 단순명으로 기록.
 */
class IngestionServiceTest {

    @Test
    void apiExceptionYieldsErrorCodeName() {
        assertThat(IngestionService.errorCodeOf(new ApiException(ErrorCode.INVALID_QUERY, "bad")))
                .isEqualTo("INVALID_QUERY");
    }

    @Test
    void otherRuntimeExceptionYieldsSimpleClassName() {
        assertThat(IngestionService.errorCodeOf(new IllegalStateException("x")))
                .isEqualTo("IllegalStateException");
        assertThat(IngestionService.errorCodeOf(new RuntimeException()))
                .isEqualTo("RuntimeException");
    }
}
