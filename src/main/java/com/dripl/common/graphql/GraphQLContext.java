package com.dripl.common.graphql;

import com.dripl.common.exception.BadRequestException;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class GraphQLContext {

    private GraphQLContext() {}

    public static UUID workspaceId() {
        Claims claims = claims();
        String value = claims.get("workspace_id", String.class);
        if (value == null || value.isBlank()) {
            throw new BadRequestException("JWT missing workspace_id claim");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid workspace_id format in JWT");
        }
    }

    public static UUID userId() {
        Claims claims = claims();
        String value = claims.get("user_id", String.class);
        if (value == null || value.isBlank()) {
            throw new BadRequestException("JWT missing user_id claim");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid user_id format in JWT");
        }
    }

    private static Claims claims() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Claims)) {
            throw new BadRequestException("Not authenticated");
        }
        return (Claims) auth.getPrincipal();
    }
}
