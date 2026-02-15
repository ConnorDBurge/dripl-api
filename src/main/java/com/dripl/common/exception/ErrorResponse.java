package com.dripl.common.exception;

import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

@Builder
public record ErrorResponse(
        int status,
        String detail,
        String correlationId,
        String path,
        List<ValidationError> errors
) {
    public ErrorResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public ErrorResponse addError(String field, String message) {
        List<ValidationError> newErrors = new ArrayList<>(this.errors);
        newErrors.add(new ValidationError(field, message));
        return new ErrorResponse(status, detail, correlationId, path, newErrors);
    }

    public record ValidationError(String field, String message) {}
}
