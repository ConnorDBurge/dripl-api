package com.dripl.auth;

import lombok.Data;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/dev")
@Profile("dev")
public class DevTokenController {

    private final JwtUtil jwtUtil;

    public DevTokenController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, String>> generateToken(@RequestBody DevTokenRequest request) {
        UUID userId = request.getUserId() != null ? request.getUserId() : UUID.randomUUID();
        UUID workspaceId = request.getWorkspaceId() != null ? request.getWorkspaceId() : UUID.randomUUID();
        String subject = request.getSubject() != null ? request.getSubject() : userId.toString();
        List<String> roles = request.getRoles() != null ? request.getRoles() : List.of("READ", "WRITE");

        String token = jwtUtil.generateToken(userId, workspaceId, subject, roles);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "user_id", userId.toString(),
                "workspace_id", workspaceId.toString(),
                "roles", String.join(", ", roles)
        ));
    }

    @Data
    public static class DevTokenRequest {
        private UUID userId;
        private UUID workspaceId;
        private String subject;
        private List<String> roles;
    }
}
