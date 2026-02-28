package com.dripl.auth.controller;

import com.dripl.auth.dto.AuthResponse;
import com.dripl.auth.dto.GoogleLoginRequest;
import com.dripl.auth.service.GoogleOAuthService;
import com.dripl.auth.service.TokenService;
import com.dripl.user.entity.User;
import com.dripl.user.service.UserService;
import com.dripl.workspace.membership.enums.Role;
import com.dripl.workspace.membership.service.MembershipService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final GoogleOAuthService googleOAuthService;
    private final UserService userService;
    private final TokenService tokenService;
    private final MembershipService membershipService;

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        GoogleIdToken.Payload payload = googleOAuthService.verifyIdToken(request.getIdToken());
        if (payload == null) {
            return ResponseEntity.status(401).build();
        }

        String email = payload.getEmail();
        String givenName = (String) payload.get("given_name");
        String familyName = (String) payload.get("family_name");

        User user = userService.bootstrapUser(email, givenName, familyName);
        String token = tokenService.mintToken(user.getId(), user.getLastWorkspaceId());

        List<String> roles = membershipService.findMembership(user.getId(), user.getLastWorkspaceId())
                .map(m -> m.getRoles().stream().map(Role::name).collect(Collectors.toList()))
                .orElse(List.of());

        log.info("Google login successful for user {} ({})", user.getId(), email);

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .workspaceId(user.getLastWorkspaceId())
                .email(email)
                .givenName(user.getGivenName())
                .familyName(user.getFamilyName())
                .roles(roles)
                .build());
    }
}
