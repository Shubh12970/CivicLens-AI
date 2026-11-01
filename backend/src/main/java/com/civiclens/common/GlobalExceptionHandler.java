package com.civiclens.common;

import com.civiclens.config.TraceIdFilter;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

/**
 * Maps every exception to {@link ApiError}. Clients never see stack traces, SQL or provider details;
 * full diagnostics are logged server-side with the trace id.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return build(ex.code(), ex.getMessage(), ex.details(), ex.data());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<ApiError.Detail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.Detail(fe.getField(), fe.getDefaultMessage()))
                .toList();
        if (details.isEmpty()) {
            details = ex.getBindingResult().getGlobalErrors().stream()
                    .map(ge -> new ApiError.Detail(ge.getObjectName(), ge.getDefaultMessage())).toList();
        }
        return build(ErrorCode.VALIDATION_ERROR, "The request contains invalid fields.", details, null);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex) {
        List<ApiError.Detail> details = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new ApiError.Detail(r.getMethodParameter().getParameterName(), e.getDefaultMessage())))
                .toList();
        return build(ErrorCode.VALIDATION_ERROR, "The request contains invalid fields.", details, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex) {
        List<ApiError.Detail> details = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.Detail(lastNode(v.getPropertyPath().toString()), v.getMessage())).toList();
        return build(ErrorCode.VALIDATION_ERROR, "The request contains invalid fields.", details, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(ErrorCode.MALFORMED_REQUEST,
                "The request body is missing, is not valid JSON, or contains a value of the wrong type or an unknown enum value.",
                List.of(), null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(ErrorCode.VALIDATION_ERROR, "The request contains invalid fields.",
                List.of(new ApiError.Detail(ex.getName(), "Invalid value.")), null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(ErrorCode.VALIDATION_ERROR, "The request contains invalid fields.",
                List.of(new ApiError.Detail(ex.getParameterName(), "Parameter is required.")), null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        return build(ErrorCode.VALIDATION_ERROR, "The request contains invalid fields.",
                List.of(new ApiError.Detail(ex.getHeaderName(), "Header is required.")), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        return build(ErrorCode.NOT_FOUND, "No endpoint exists at this path.", List.of(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED, "HTTP method not supported for this path.", List.of(), null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json.", List.of(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
            return build(ErrorCode.UNAUTHORIZED, "Authentication is required.", List.of(), null);
        }
        return build(ErrorCode.FORBIDDEN, "You do not have permission to perform this action.", List.of(), null);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiError> handleLocking(Exception ex) {
        log.info("Concurrent modification rejected: {}", ex.getClass().getSimpleName());
        return build(ErrorCode.CONFLICT, "The resource was modified concurrently. Reload it and try again.", List.of(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return build(ErrorCode.CONFLICT, "The request conflicts with existing data.", List.of(), null);
    }

    @ExceptionHandler({CannotCreateTransactionException.class, DataAccessResourceFailureException.class,
            CannotGetJdbcConnectionException.class})
    public ResponseEntity<ApiError> handleDatabaseDown(Exception ex) {
        log.error("Database unavailable", ex);
        return build(ErrorCode.SERVICE_UNAVAILABLE, "The service is temporarily unavailable. Please retry shortly.",
                List.of(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", List.of(), null);
    }

    public static ApiError body(ErrorCode code, String message, List<ApiError.Detail> details, Object data) {
        return new ApiError(code.name(), message, details, MDC.get(TraceIdFilter.MDC_KEY), Instant.now(), data);
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message, List<ApiError.Detail> details, Object data) {
        return ResponseEntity.status(code.status()).body(body(code, message, details, data));
    }

    private static String lastNode(String path) {
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
