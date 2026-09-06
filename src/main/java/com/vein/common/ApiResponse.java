package com.vein.common;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Common success envelope: {@code {data, meta}} (부록 C-1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, Meta meta) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Meta(String traceId, String freshness, String nextCursor, Integer unreadCount) {
    }

    public static <T> ApiResponse<T> of(T data, Meta meta) {
        return new ApiResponse<>(data, meta);
    }

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, new Meta(TraceIdFilter.currentTraceId(), null, null, null));
    }

    public static <T> ApiResponse<T> of(T data, String freshness) {
        return new ApiResponse<>(data,
                new Meta(TraceIdFilter.currentTraceId(), freshness, null, null));
    }

    public static <T> ApiResponse<List<T>> list(List<T> data, String nextCursor) {
        return new ApiResponse<>(data,
                new Meta(TraceIdFilter.currentTraceId(), null, nextCursor, null));
    }

    public static <T> ApiResponse<List<T>> list(List<T> data, String nextCursor, Integer unreadCount) {
        return new ApiResponse<>(data,
                new Meta(TraceIdFilter.currentTraceId(), null, nextCursor, unreadCount));
    }
}
