package com.dripl.common;

import com.dripl.common.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/test");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void handleAccessDenied_returns403() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(
                new AccessDeniedException("Forbidden"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().detail()).isEqualTo("Forbidden");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/test");
    }

    @Test
    void handleBadRequest_returns400() {
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(
                new BadRequestException("Bad input"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().detail()).isEqualTo("Bad input");
    }

    @Test
    void handleConflict_returns409() {
        ResponseEntity<ErrorResponse> response = handler.handleConflict(
                new ConflictException("Already exists"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().detail()).isEqualTo("Already exists");
    }

    @Test
    void handleResourceNotFound_returns404() {
        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(
                new ResourceNotFoundException("Not found"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().detail()).isEqualTo("Not found");
    }

    @Test
    void handleAuthorizationDenied_returns403() {
        ResponseEntity<ErrorResponse> response = handler.handleAuthorizationDenied(
                new AuthorizationDeniedException("Access denied"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().detail()).isEqualTo("Access denied");
    }

    @Test
    void handleConstraintViolation_returns400() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("Bad JSON");

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handleMethodArgumentNotValid_returns400WithFieldErrors() throws Exception {
        BindingResult bindingResult = mock(BindingResult.class);

        FieldError fieldError1 = new FieldError("dto", "email", "Email is not valid");
        FieldError fieldError2 = new FieldError("dto", "name", "Name must be provided");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

        // Use a real MethodParameter to avoid NPE on getExecutable()
        MethodParameter parameter = new MethodParameter(
                this.getClass().getDeclaredMethod("handleMethodArgumentNotValid_returns400WithFieldErrors"), -1);

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentNotValid(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().detail()).isEqualTo("Validation failed");
        assertThat(response.getBody().errors()).hasSize(2);
        assertThat(response.getBody().errors().get(0).field()).isEqualTo("email");
        assertThat(response.getBody().errors().get(1).field()).isEqualTo("name");
    }

    @Test
    void handleGeneric_returns500() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new RuntimeException("Something broke"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().detail()).isEqualTo("Woops, we ran into an error");
    }

    @Test
    void correlationId_usesMdcIfPresent() {
        MDC.put("correlationId", "my-correlation-id");

        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(
                new BadRequestException("test"), request);

        assertThat(response.getBody().correlationId()).isEqualTo("my-correlation-id");
    }

    @Test
    void correlationId_generatesUuidIfMdcEmpty() {

        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(
                new BadRequestException("test"), request);

        assertThat(response.getBody().correlationId()).isNotNull().isNotBlank();
    }

    @Test
    void errorResponse_errorsDefaultsToEmptyList() {
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(
                new BadRequestException("test"), request);

        assertThat(response.getBody().errors()).isEmpty();
    }

    @Test
    void handleBadRequest_nullMessage_usesStatusReason() {
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(
                new BadRequestException(null), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().detail()).isEqualTo("Bad Request");
    }
}
