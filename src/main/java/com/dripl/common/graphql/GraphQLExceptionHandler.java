package com.dripl.common.graphql;

import com.dripl.common.exception.AccessDeniedException;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ConflictException;
import com.dripl.common.exception.ResourceNotFoundException;
import graphql.GraphQLError;
import graphql.ErrorClassification;
import jakarta.validation.ConstraintViolationException;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.stream.Collectors;

@ControllerAdvice
public class GraphQLExceptionHandler {

    private enum DriplErrorType implements ErrorClassification {
        BAD_REQUEST, NOT_FOUND, FORBIDDEN, CONFLICT, VALIDATION_ERROR
    }

    @GraphQlExceptionHandler
    public GraphQLError handleBadRequest(BadRequestException ex) {
        return GraphQLError.newError()
                .message(ex.getMessage())
                .errorType(DriplErrorType.BAD_REQUEST)
                .build();
    }

    @GraphQlExceptionHandler
    public GraphQLError handleNotFound(ResourceNotFoundException ex) {
        return GraphQLError.newError()
                .message(ex.getMessage())
                .errorType(DriplErrorType.NOT_FOUND)
                .build();
    }

    @GraphQlExceptionHandler
    public GraphQLError handleAccessDenied(AccessDeniedException ex) {
        return GraphQLError.newError()
                .message(ex.getMessage())
                .errorType(DriplErrorType.FORBIDDEN)
                .build();
    }

    @GraphQlExceptionHandler
    public GraphQLError handleConflict(ConflictException ex) {
        return GraphQLError.newError()
                .message(ex.getMessage())
                .errorType(DriplErrorType.CONFLICT)
                .build();
    }

    @GraphQlExceptionHandler
    public GraphQLError handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
        return GraphQLError.newError()
                .message(message)
                .errorType(DriplErrorType.VALIDATION_ERROR)
                .build();
    }
}
