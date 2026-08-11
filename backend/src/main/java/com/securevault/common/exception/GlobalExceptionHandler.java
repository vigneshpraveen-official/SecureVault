package com.securevault.common.exception;

import com.securevault.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Single, consolidated handler (P2.3/M-26, M-27) — replaces the per-controller @ExceptionHandler
 * methods S1.1-S1.6 used as a stopgap. One handler per exception category, not per concrete
 * exception, since every BusinessException already carries its own ErrorCode/HttpStatus.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode().name(), null));
    }

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
                .body(
                        ApiResponse.error(
                                "Validation failed", ErrorCode.VALIDATION_FAILED.name(), errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex) {
        var errors =
                ex.getConstraintViolations().stream()
                        .map(
                                cv ->
                                        new ApiResponse.FieldError(
                                                cv.getPropertyPath().toString(), cv.getMessage()))
                        .toList();
        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.error(
                                "Validation failed", ErrorCode.VALIDATION_FAILED.name(), errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedBody(
            HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.error(
                                "Malformed request body",
                                ErrorCode.VALIDATION_FAILED.name(),
                                null));
    }

    // e.g. GET /api/vault/not-a-number — a client sending the wrong type for a path/request
    // parameter is a 400, not a 500; found during the P2.4 audit sweep.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        var error =
                new ApiResponse.FieldError(
                        ex.getName(), "must be a valid " + ex.getRequiredType().getSimpleName());
        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.error(
                                "Validation failed",
                                ErrorCode.VALIDATION_FAILED.name(),
                                List.of(error)));
    }

    // e.g. GET /api/vault/search with no ?q= — same reasoning as the type-mismatch handler
    // above: a missing required parameter is a 400, not a 500; found during the same sweep.
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException ex) {
        var error = new ApiResponse.FieldError(ex.getParameterName(), "must not be missing");
        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.error(
                                "Validation failed",
                                ErrorCode.VALIDATION_FAILED.name(),
                                List.of(error)));
    }

    // Safety net for AuthenticationException subtypes that reach a controller method without
    // being translated into InvalidCredentialsException first (AuthController.login() already
    // catches BadCredentialsException/UsernameNotFoundException itself).
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(
                        ApiResponse.error(
                                "Authentication required",
                                ErrorCode.INVALID_CREDENTIALS.name(),
                                null));
    }

    // The framework's own AccessDeniedException (e.g. from @PreAuthorize), distinct from
    // com.securevault.common.exception.AccessDeniedException, which BusinessException already
    // handles above. Referenced fully-qualified to avoid a name collision with our own type.
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleFrameworkAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied", ErrorCode.ACCESS_DENIED.name(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unhandled exception [{}]", correlationId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ApiResponse.error(
                                "An unexpected error occurred. Reference: " + correlationId,
                                ErrorCode.INTERNAL_ERROR.name(),
                                null));
    }
}
