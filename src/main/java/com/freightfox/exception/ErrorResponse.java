package com.freightfox.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard API error contract for all REST endpoints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorDetail> details,
        String traceId
) {
    public record FieldErrorDetail(String field, String message) {}

    public static ErrorResponse of(int status, String error, String message, String path, List<FieldErrorDetail> details, String traceId) {
        return new ErrorResponse(Instant.now(), status, error, message, path, details, traceId);
    }

    public static ErrorResponse of(int status, String error, String message, String path, String traceId) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null, traceId);
    }
}
