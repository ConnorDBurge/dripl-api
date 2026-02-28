package com.dripl.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private String token;
    private UUID userId;
    private UUID workspaceId;
    private String email;
    private String givenName;
    private String familyName;
    private List<String> roles;
}
