package com.securevault.common.exception;

import com.securevault.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Basic global handler added in S1.6 to close a Milestone 1 quality gap — covers Bean Validation
 * failures and any otherwise-uncaught exception so no response ever leaks a raw stack trace or
 * bypasses the ApiResponse envelope. Per-controller handlers for specific business exceptions
 * (DuplicateEmailException, etc.) stay where they are for now. TODO(S2.3): consolidate everything
 * into one comprehensive @ControllerAdvice with the full ErrorCode enum (M-26/M-27).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        var errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                fe ->
                                        new ApiResponse.FieldError(
                                                fe.getField(), fe.getDefaultMessage()))
                        .toList();
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Validation failed", "VALIDATION_FAILED", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred", "INTERNAL_ERROR", null));
    }
}
