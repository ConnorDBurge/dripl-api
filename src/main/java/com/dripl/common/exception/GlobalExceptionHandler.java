package com.dripl.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleConstraintViolation(Exception ex, HttpServletRequest request) {
        return buildAndLog(HttpStatus.BAD_REQUEST, ex.getLocalizedMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
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

    private ResponseEntity<ErrorResponse> buildAndLog(HttpStatus status, String message, HttpServletRequest request) {
        String correlationId = resolveCorrelationId(request);
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

    private String resolveCorrelationId(HttpServletRequest request) {
        String id = request.getHeader("X-Correlation-Id");
        return (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
    }

    private String safePath(HttpServletRequest request) {
        try {
            return request.getRequestURI();
        } catch (Exception e) {
            return "";
        }
    }
}
