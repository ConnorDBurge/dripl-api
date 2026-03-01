package com.dripl.auth.controller;

import com.dripl.auth.dto.AuthResponse;
import com.dripl.auth.service.TokenService;
import com.dripl.user.entity.User;
import com.dripl.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/dev/auth")
@RequiredArgsConstructor
@Profile("dev")
public class DevAuthController {

    private final UserService userService;
    private final TokenService tokenService;

    @PostMapping("/token")
    public ResponseEntity<AuthResponse> devToken(@RequestParam String email) {
        User user = userService.bootstrapUser(email, null, null);
        String token = tokenService.mintToken(user.getId(), user.getLastWorkspaceId());

        log.warn("[DEV] Issuing token for {} without authentication", email);

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .workspaceId(user.getLastWorkspaceId())
                .email(user.getEmail())
                .givenName(user.getGivenName())
                .familyName(user.getFamilyName())
                .build());
    }
}
