package com.vein.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;

/**
 * R43 견고성: 잘못된 요청이 500이 아니라 올바른 4xx + 앱 에러 봉투로 매핑되는지 검증.
 * 순수 단위(스프링 컨텍스트/DB 불필요).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void badParamType_maps_to_400_with_field() {
        var ex = new MethodArgumentTypeMismatchException("abc", Integer.class, "days", null, null);
        ResponseEntity<ApiError> resp = handler.handleBadRequest(ex);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().error().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(resp.getBody().error().fieldErrors()).isNotNull();
        assertThat(resp.getBody().error().fieldErrors().get(0).field()).isEqualTo("days");
    }

    @Test
    void missingParam_maps_to_400() {
        var ex = new MissingServletRequestParameterException("market", "String");
        ResponseEntity<ApiError> resp = handler.handleBadRequest(ex);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().error().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void constraintViolation_maps_to_400() {
        var ex = new ConstraintViolationException(Collections.emptySet());
        ResponseEntity<ApiError> resp = handler.handleConstraint(ex);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().error().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void wrongMethod_maps_to_405() {
        var ex = new HttpRequestMethodNotSupportedException("DELETE");
        ResponseEntity<ApiError> resp = handler.handleMethodNotAllowed(ex);

        assertThat(resp.getStatusCode().value()).isEqualTo(405);
        assertThat(resp.getBody().error().code()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void unmappedPath_noResource_maps_to_404() {
        var ex = new NoResourceFoundException(HttpMethod.GET, "/api/v1/nonexistent");
        ResponseEntity<ApiError> resp = handler.handleNotFound(ex);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().error().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void unmappedPath_noHandler_maps_to_404() {
        var ex = new NoHandlerFoundException("GET", "/api/v1/foo/bar", new HttpHeaders());
        ResponseEntity<ApiError> resp = handler.handleNotFound(ex);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        assertThat(resp.getBody().error().code()).isEqualTo("NOT_FOUND");
    }
}
