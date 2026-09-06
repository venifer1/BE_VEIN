package com.vein.common;

import java.util.List;

import lombok.Getter;

/**
 * Application exception carrying an {@link ErrorCode}. Translated to the
 * common error envelope by {@link GlobalExceptionHandler}.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient List<FieldError> fieldErrors;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), List.of());
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public ApiException(ErrorCode errorCode, String message, List<FieldError> fieldErrors) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors == null ? List.of() : fieldErrors;
    }

    /** Convenience for string error-code names used in the docx skeleton (e.g. "AUTH_INVALID"). */
    public ApiException(String errorCodeName) {
        this(ErrorCode.valueOf(errorCodeName));
    }

    public record FieldError(String field, String reason) {
    }
}
