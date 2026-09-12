package com.vein.common;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        ErrorCode code = ex.getErrorCode();
        ApiError body = ApiError.of(code.name(), ex.getMessage(),
                TraceIdFilter.currentTraceId(), ex.getFieldErrors());
        return ResponseEntity.status(code.status()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiException.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        ApiError body = ApiError.of(ErrorCode.VALIDATION_ERROR.name(), "Validation failed",
                TraceIdFilter.currentTraceId(), fieldErrors);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status()).body(body);
    }

    /**
     * Bad request shape — wrong query-param type / unparseable enum ({@code ?days=abc},
     * {@code ?type=FOO}), missing required param, or malformed/absent JSON body. These are
     * <b>client errors</b>: return 400 with the app envelope instead of leaking a 500.
     */
    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class,
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
        String field = null;
        String message = "Malformed request";
        if (ex instanceof MethodArgumentTypeMismatchException e) {
            field = e.getName();
            message = "Invalid value for '" + e.getName() + "'";
        } else if (ex instanceof MissingServletRequestParameterException e) {
            field = e.getParameterName();
            message = "Missing required parameter '" + e.getParameterName() + "'";
        } else {
            message = "Malformed request body";
        }
        List<ApiException.FieldError> fieldErrors = field == null
                ? null : List.of(new ApiException.FieldError(field, message));
        ApiError body = ApiError.of(ErrorCode.VALIDATION_ERROR.name(), message,
                TraceIdFilter.currentTraceId(), fieldErrors);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status()).body(body);
    }

    /** Constraint violations on {@code @RequestParam}/{@code @PathVariable} (@Validated). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex) {
        List<ApiException.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(v -> new ApiException.FieldError(
                        v.getPropertyPath() == null ? "param" : v.getPropertyPath().toString(),
                        v.getMessage() == null ? "INVALID" : v.getMessage()))
                .toList();
        ApiError body = ApiError.of(ErrorCode.VALIDATION_ERROR.name(), "Validation failed",
                TraceIdFilter.currentTraceId(), fieldErrors);
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.status()).body(body);
    }

    /** Wrong HTTP method on a known path → 405 instead of a leaked 500. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        ApiError body = ApiError.of(ErrorCode.METHOD_NOT_ALLOWED.name(),
                "Method " + ex.getMethod() + " not allowed",
                TraceIdFilter.currentTraceId(), null);
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.status()).body(body);
    }

    /**
     * 미매핑 경로(오타 URL·존재하지 않는 리소스)는 <b>클라이언트 404</b>다. R43이 잘못된 요청을
     * 4xx로 정규화했지만, 인증을 통과한 미매핑 경로는 catch-all에 걸려 500 + ERROR 로그로 새고
     * 있었다. 여기서 404로 매핑해 로그 오염과 "재시도 가능한 서버 오류" 오인을 막는다.
     * (미인증 미매핑 경로는 시큐리티가 먼저 401을 낸다.)
     */
    @ExceptionHandler({ NoResourceFoundException.class, NoHandlerFoundException.class })
    public ResponseEntity<ApiError> handleNotFound(Exception ex) {
        ApiError body = ApiError.of(ErrorCode.NOT_FOUND.name(), "No handler for the requested path",
                TraceIdFilter.currentTraceId(), null);
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        ApiError body = ApiError.of(ErrorCode.INTERNAL_ERROR.name(), "Internal error",
                TraceIdFilter.currentTraceId(), null);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status()).body(body);
    }

    private ApiException.FieldError toFieldError(FieldError fe) {
        return new ApiException.FieldError(fe.getField(),
                fe.getDefaultMessage() == null ? "INVALID" : fe.getDefaultMessage());
    }
}
