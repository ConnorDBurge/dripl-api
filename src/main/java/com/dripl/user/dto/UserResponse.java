package com.dripl.user.dto;

import com.dripl.user.entity.User;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class UserResponse extends UserDto {
    private String token;

    public static UserResponse fromEntity(User user, String token) {
        return UserResponse.builder()
                .id(user.getId())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .email(user.getEmail())
                .givenName(user.getGivenName())
                .familyName(user.getFamilyName())
                .isActive(user.getIsActive())
                .lastWorkspaceId(user.getLastWorkspaceId())
                .token(token)
                .build();
    }
}
