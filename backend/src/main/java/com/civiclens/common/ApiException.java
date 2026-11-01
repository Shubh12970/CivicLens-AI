package com.civiclens.common;

import java.util.List;

/** Business exception mapped to the stable error format by {@link GlobalExceptionHandler}. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient List<ApiError.Detail> details;
    private final transient Object data;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of(), null);
    }

    public ApiException(ErrorCode code, String message, List<ApiError.Detail> details, Object data) {
        super(message);
        this.code = code;
        this.details = details == null ? List.of() : details;
        this.data = data;
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " was not found.");
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, "The request contains invalid fields.",
                List.of(new ApiError.Detail(field, message)), null);
    }

    public ErrorCode code() {
        return code;
    }

    public List<ApiError.Detail> details() {
        return details;
    }

    /** Optional structured payload (e.g. duplicate candidates) returned in the error body's {@code data}. */
    public Object data() {
        return data;
    }
}
