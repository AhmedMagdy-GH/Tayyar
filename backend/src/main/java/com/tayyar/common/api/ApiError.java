package com.tayyar.common.api;

import java.time.Instant;
import java.util.List;

public record ApiError(String code, String message, Instant timestamp, String path,
                       String correlationId, List<FieldViolation> fieldErrors) {
    public record FieldViolation(String field, String message) {}
}
