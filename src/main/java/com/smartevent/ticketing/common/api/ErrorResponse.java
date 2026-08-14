package com.smartevent.ticketing.common.api;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse (
        boolean success,
        String code,
        String message,
        String path,
        Map<String, String> errors,
        Instant timestamp
) {
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(false, code, message, path, Map.of(), Instant.now());
    }

    public  static ErrorResponse of(String code, String message, String path, Map<String, String> errors) {
        return new ErrorResponse(false, code, message, path, errors, Instant.now());
    }
}
