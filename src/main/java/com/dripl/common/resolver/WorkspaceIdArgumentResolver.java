package com.dripl.common.resolver;

import com.dripl.common.annotation.WorkspaceId;
import com.dripl.common.exception.BadRequestException;
import io.jsonwebtoken.Claims;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.UUID;

public class WorkspaceIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(WorkspaceId.class)
                && parameter.getParameterType().equals(UUID.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Claims claims)) {
            throw new BadRequestException("No JWT token found");
        }

        String workspaceId = claims.get("workspace_id", String.class);
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new BadRequestException("JWT missing workspace_id claim");
        }

        try {
            return UUID.fromString(workspaceId);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid workspace_id format in JWT");
        }
    }
}
