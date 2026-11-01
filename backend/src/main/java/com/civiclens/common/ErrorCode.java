package com.civiclens.common;

import org.springframework.http.HttpStatus;

/** Stable, documented error codes. The frontend should branch on {@code code}, never on {@code message}. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    CONFLICT(HttpStatus.CONFLICT),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT),
    POSSIBLE_DUPLICATE(HttpStatus.CONFLICT),
    ANALYSIS_IN_PROGRESS(HttpStatus.CONFLICT),
    ALREADY_REVIEWED(HttpStatus.CONFLICT),
    REVIEW_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_ENTITY),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_ENTITY),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
