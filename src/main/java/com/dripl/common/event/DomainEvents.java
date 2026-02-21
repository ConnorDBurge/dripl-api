package com.dripl.common.event;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DomainEvents {

    private DomainEvents() {}

    public static DomainEvent create(String domain, String action, UUID entityId, UUID workspaceId, List<FieldChange> changes) {
        return new DomainEvent(domain, action, entityId, workspaceId, changes, currentUser(), LocalDateTime.now());
    }

    private static String currentUser() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(Claims.class::isInstance)
                .map(Claims.class::cast)
                .map(Claims::getSubject)
                .orElse(null);
    }
}
