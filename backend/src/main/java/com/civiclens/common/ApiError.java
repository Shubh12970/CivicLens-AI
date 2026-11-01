package com.civiclens.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/** The single error shape returned by every endpoint. */
public record ApiError(
        String code,
        String message,
        List<Detail> details,
        String traceId,
        Instant timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object data) {

    public record Detail(String field, String message) {
    }
}
