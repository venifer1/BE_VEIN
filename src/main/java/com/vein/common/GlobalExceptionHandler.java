package com.vein.common;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
