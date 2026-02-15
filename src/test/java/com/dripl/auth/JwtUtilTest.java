package com.dripl.auth;

import com.dripl.auth.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        String secret = "this-is-a-test-secret-key-that-is-at-least-32-bytes-long";
        jwtUtil = new JwtUtil(secret, 3600000);
    }

    @Test
    void generateToken_containsAllClaims() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        String token = jwtUtil.generateToken(userId, workspaceId, "connor@test.com", List.of("OWNER", "READ"));

        Claims claims = jwtUtil.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo("connor@test.com");
        assertThat(claims.get("user_id", String.class)).isEqualTo(userId.toString());
        assertThat(claims.get("workspace_id", String.class)).isEqualTo(workspaceId.toString());
        assertThat(claims.get("roles", List.class)).containsExactly("OWNER", "READ");
    }

    @Test
    void isValid_validToken_returnsTrue() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        String token = jwtUtil.generateToken(userId, workspaceId, "test@test.com", List.of("READ"));

        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    void isValid_invalidToken_returnsFalse() {
        assertThat(jwtUtil.isValid("not.a.valid.token")).isFalse();
    }

    @Test
    void isValid_tamperedToken_returnsFalse() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        String token = jwtUtil.generateToken(userId, workspaceId, "test@test.com", List.of("READ"));
        // Flip a character in the middle of the signature to ensure invalid
        char[] chars = token.toCharArray();
        int idx = token.lastIndexOf('.') + 5;
        chars[idx] = chars[idx] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        assertThat(jwtUtil.isValid(tampered)).isFalse();
    }

    @Test
    void isValid_expiredToken_returnsFalse() {
        JwtUtil expiredJwtUtil = new JwtUtil(
                "this-is-a-test-secret-key-that-is-at-least-32-bytes-long", 0);
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        String token = expiredJwtUtil.generateToken(userId, workspaceId, "test@test.com", List.of("READ"));

        assertThat(expiredJwtUtil.isValid(token)).isFalse();
    }

    @Test
    void parseToken_roundTrips() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        String token = jwtUtil.generateToken(userId, workspaceId, "connor@test.com", List.of("OWNER"));
        Claims claims = jwtUtil.parseToken(token);

        assertThat(UUID.fromString(claims.get("user_id", String.class))).isEqualTo(userId);
        assertThat(UUID.fromString(claims.get("workspace_id", String.class))).isEqualTo(workspaceId);
    }

    @Test
    void isValid_differentSecret_returnsFalse() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        String token = jwtUtil.generateToken(userId, workspaceId, "test@test.com", List.of("READ"));

        JwtUtil otherJwtUtil = new JwtUtil(
                "a-completely-different-secret-that-is-32-bytes-long!", 3600000);

        assertThat(otherJwtUtil.isValid(token)).isFalse();
    }
}
