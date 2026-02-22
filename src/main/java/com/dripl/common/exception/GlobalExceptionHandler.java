package com.dripl.common.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private String resolveCorrelationId() {
        String id = MDC.get("correlationId");
        return (id != null && !id.isBlank()) ? id : UUID.randomUUID().toString();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildAndLog(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return buildAndLog(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return buildAndLog(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildAndLog(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(AuthorizationDeniedException ex, HttpServletRequest request) {
        return buildAndLog(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        return buildAndLog(HttpStatus.BAD_REQUEST, ex.getLocalizedMessage(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String message = buildEnumErrorMessage(ex);
        return buildAndLog(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId();
        String path = safePath(request);
        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .detail("Validation failed")
                .correlationId(correlationId)
                .path(path)
                .errors(ex.getFieldErrors().stream()
                        .map(fe -> new ErrorResponse.ValidationError(fe.getField(), fe.getDefaultMessage()))
                        .toList())
                .build();
        log.warn("{} at {}: {}", HttpStatus.BAD_REQUEST, path, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String paramName = ex.getName();
        String value = ex.getValue() != null ? ex.getValue().toString() : "null";
        Class<?> requiredType = ex.getRequiredType();
        String typeName = requiredType != null ? requiredType.getSimpleName() : "unknown";
        String message = String.format("Invalid value '%s' for parameter '%s'. Expected type: %s", value, paramName, typeName);
        return buildAndLog(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());
        return buildAndLog(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId();
        String path = safePath(request);
        ErrorResponse body = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .detail("Woops, we ran into an error")
                .correlationId(correlationId)
                .path(path)
                .build();
        log.error("{} at {}: {}", HttpStatus.INTERNAL_SERVER_ERROR, path, ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @SuppressWarnings("rawtypes")
    private String buildEnumErrorMessage(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof InvalidFormatException ife && ife.getTargetType().isEnum()) {
            String fieldName = ife.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .reduce((a, b) -> b)
                    .orElse("unknown");

            Class<? extends Enum> enumType = (Class<? extends Enum>) ife.getTargetType();
            String allowedValues = Arrays.stream(enumType.getEnumConstants())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));

            return String.format("Invalid value '%s' for field '%s'. Allowed values: [%s]",
                    ife.getValue(), fieldName, allowedValues);
        }
        return "Malformed JSON request";
    }

    private ResponseEntity<ErrorResponse> buildAndLog(HttpStatus status, String message, HttpServletRequest request) {
        String correlationId = resolveCorrelationId();
        String path = safePath(request);
        ErrorResponse body = ErrorResponse.builder()
                .status(status.value())
                .detail(message != null ? message : status.getReasonPhrase())
                .correlationId(correlationId)
                .path(path)
                .build();
        log.warn("{} at {}: {}", status, path, message);
        return ResponseEntity.status(status).body(body);
    }

    private String safePath(HttpServletRequest request) {
        try {
            return request.getRequestURI();
        } catch (Exception e) {
            return "";
        }
    }
}
