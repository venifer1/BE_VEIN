package com.vein.common;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Common error envelope: {@code {error:{code,message,trace_id,field_errors}}} (부록 C-1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Body error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String code, String message, String traceId, List<ApiException.FieldError> fieldErrors) {
    }

    public static ApiError of(String code, String message, String traceId,
                              List<ApiException.FieldError> fieldErrors) {
        List<ApiException.FieldError> fe = (fieldErrors == null || fieldErrors.isEmpty()) ? null : fieldErrors;
        return new ApiError(new Body(code, message, traceId, fe));
    }

    /** Mapper configured for snake_case, used by the security entry-point writer. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /**
     * Low-level writer used by Security entry-points/handlers where no
     * {@code @RestControllerAdvice} is available (부록 H-1).
     */
    public static void write(HttpServletResponse res, int httpStatus, String code) throws IOException {
        res.setStatus(httpStatus);
        res.setContentType("application/json;charset=UTF-8");
        ApiError body = ApiError.of(code, code, TraceIdFilter.currentTraceId(), null);
        MAPPER.writeValue(res.getWriter(), body);
    }
}
