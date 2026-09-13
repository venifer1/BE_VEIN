package com.vein.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vein.common.ApiException.FieldError;

/**
 * 공통 에러 봉투(부록 C-1) 빌더 회귀 보호(R173). 빈/누락 field_errors는 null로 접어
 * (NON_NULL) 응답에서 키가 사라지도록 한다.
 */
class ApiErrorTest {

    @Test
    void collapsesNullOrEmptyFieldErrorsToNull() {
        assertThat(ApiError.of("BAD", "bad", "t1", null).error().fieldErrors()).isNull();
        assertThat(ApiError.of("BAD", "bad", "t1", List.of()).error().fieldErrors()).isNull();
    }

    @Test
    void keepsNonEmptyFieldErrors() {
        List<FieldError> fe = List.of(new FieldError("email", "required"));
        ApiError e = ApiError.of("VALIDATION", "invalid", "t2", fe);
        assertThat(e.error().fieldErrors()).containsExactlyElementsOf(fe);
    }

    @Test
    void carriesCodeMessageTraceId() {
        ApiError e = ApiError.of("NOT_FOUND", "missing", "trace-9", null);
        assertThat(e.error().code()).isEqualTo("NOT_FOUND");
        assertThat(e.error().message()).isEqualTo("missing");
        assertThat(e.error().traceId()).isEqualTo("trace-9");
    }
}
