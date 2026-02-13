package com.dripl.auth;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ProtectedController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Claims claims = (Claims) auth.getPrincipal();

        return Map.of(
                "message", "pong",
                "user_id", claims.get("user_id"),
                "workspace_id", claims.get("workspace_id"),
                "roles", claims.get("roles")
        );
    }
}
