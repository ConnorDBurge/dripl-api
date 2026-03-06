package com.dripl.user;

import com.dripl.common.exception.ConflictException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.user.dto.UpdateUserInput;
import com.dripl.user.dto.UserResponse;
import com.dripl.user.entity.User;
import com.dripl.user.mapper.UserMapper;
import com.dripl.user.resolver.UserResolver;
import com.dripl.user.service.UserService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserResolverTest {

    @Mock private UserService userService;
    @Spy private UserMapper userMapper = Mappers.getMapper(UserMapper.class);
    @InjectMocks private UserResolver userResolver;

    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();

    @BeforeEach
    void setUpSecurityContext() {
        Claims claims = mock(Claims.class);
        when(claims.get("user_id", String.class)).thenReturn(userId.toString());
        lenient().when(claims.get("workspace_id", String.class)).thenReturn(workspaceId.toString());
        var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User buildUser() {
        return User.builder()
                .id(userId)
                .email("connor@test.com")
                .givenName("Connor")
                .familyName("Burge")
                .isActive(true)
                .lastWorkspaceId(workspaceId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .createdBy("connor@test.com")
                .build();
    }

    @Test
    void self_returnsCurrentUser() {
        when(userService.getUser(userId)).thenReturn(buildUser());

        UserResponse result = userResolver.self();

        assertThat(result.getEmail()).isEqualTo("connor@test.com");
        assertThat(result.getGivenName()).isEqualTo("Connor");
        assertThat(result.getFamilyName()).isEqualTo("Burge");
        assertThat(result.getIsActive()).isTrue();
        assertThat(result.getLastWorkspaceId()).isEqualTo(workspaceId);
    }

    @Test
    void self_userNotFound_throwsException() {
        when(userService.getUser(userId)).thenThrow(new ResourceNotFoundException("User not found"));

        assertThatThrownBy(() -> userResolver.self())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void updateSelf_validInput_returnsUpdatedUser() {
        User updatedUser = buildUser();
        updatedUser.setEmail("new@test.com");
        when(userService.updateUser(eq(userId), any(UpdateUserInput.class))).thenReturn(updatedUser);

        UpdateUserInput input = UpdateUserInput.builder().email("new@test.com").build();
        UserResponse result = userResolver.updateSelf(input);

        assertThat(result.getEmail()).isEqualTo("new@test.com");
        verify(userService).updateUser(eq(userId), any(UpdateUserInput.class));
    }

    @Test
    void updateSelf_duplicateEmail_throwsConflict() {
        when(userService.updateUser(eq(userId), any(UpdateUserInput.class)))
                .thenThrow(new ConflictException("Email already in use"));

        UpdateUserInput input = UpdateUserInput.builder().email("taken@test.com").build();

        assertThatThrownBy(() -> userResolver.updateSelf(input))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email already in use");
    }

    @Test
    void deleteSelf_success_returnsTrue() {
        doNothing().when(userService).deleteUser(userId);

        boolean result = userResolver.deleteSelf();

        assertThat(result).isTrue();
        verify(userService).deleteUser(userId);
    }

    @Test
    void deleteSelf_userNotFound_throwsException() {
        doThrow(new ResourceNotFoundException("User not found")).when(userService).deleteUser(userId);

        assertThatThrownBy(() -> userResolver.deleteSelf())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User not found");
    }
}
