package com.dripl.user;

import com.dripl.common.annotation.UserId;
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

    @PostMapping(value = "/bootstrap")
    public ResponseEntity<UserService.BootstrapResponse> bootstrap(@RequestBody BootstrapUserDto dto) {
        UserService.BootstrapResponse response = userService.bootstrapUser(
                dto.getEmail(), dto.getGivenName(), dto.getFamilyName());
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/self", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> getSelf(@UserId UUID userId) {
        User user = userService.getUser(userId);
        return ResponseEntity.ok(UserDto.fromEntity(user));
    }

    @PatchMapping(value = "/self", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserDto> updateSelf(@UserId UUID userId, @Valid @RequestBody UpdateUserDto dto) {
        User updatedUser = userService.updateUser(userId, dto);
        return ResponseEntity.ok(UserDto.fromEntity(updatedUser));
    }

    @DeleteMapping(value = "/self")
    public ResponseEntity<Void> deleteSelf(@UserId UUID userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
