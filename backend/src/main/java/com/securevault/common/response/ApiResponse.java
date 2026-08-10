package com.securevault.common.response;

import java.time.Instant;
import java.util.List;

/** Uniform response envelope (master §9, D-11) — every controller method returns this. */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        String errorCode,
        List<FieldError> errors,
        Instant timestamp) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(
            String message, String errorCode, List<FieldError> errors) {
        return new ApiResponse<>(false, message, null, errorCode, errors, Instant.now());
    }

    public record FieldError(String field, String message) {}
}
