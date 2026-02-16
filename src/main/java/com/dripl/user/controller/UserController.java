package com.dripl.user.controller;

import com.dripl.auth.service.TokenService;
import com.dripl.common.annotation.UserId;
import com.dripl.common.annotation.WorkspaceId;
import com.dripl.user.mapper.UserMapper;
import com.dripl.user.service.UserService;
import com.dripl.user.dto.BootstrapUserDto;
import com.dripl.user.dto.UpdateUserDto;
import com.dripl.user.dto.UserDto;
import com.dripl.user.dto.UserAuthResponse;
import com.dripl.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final TokenService tokenService;
    private final UserMapper userMapper;

    @PostMapping(value = "/bootstrap")
    public ResponseEntity<UserAuthResponse> bootstrap(@Valid @RequestBody BootstrapUserDto dto) {
        User user = userService.bootstrapUser(dto.getEmail(), dto.getGivenName(), dto.getFamilyName());
        String token = tokenService.mintToken(user.getId(), user.getLastWorkspaceId());
        return ResponseEntity.ok(userMapper.toResponse(user, token));
    }

    @GetMapping(value = "/self", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> getSelf(@UserId UUID userId) {
        User user = userService.getUser(userId);
        return ResponseEntity.ok(userMapper.toDto(user));
    }

    @PatchMapping(value = "/self", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserAuthResponse> updateSelf(
            @UserId UUID userId, @WorkspaceId UUID workspaceId,
            @Valid @RequestBody UpdateUserDto dto) {
        User updatedUser = userService.updateUser(userId, dto);
        String token = tokenService.mintToken(userId, workspaceId);
        return ResponseEntity.ok(userMapper.toResponse(updatedUser, token));
    }

    @DeleteMapping(value = "/self")
    public ResponseEntity<Void> deleteSelf(@UserId UUID userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
