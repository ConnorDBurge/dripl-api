package com.dripl.user;

import com.dripl.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class UserDto extends BaseDto {
    private String email;
    private String givenName;
    private String familyName;
    private Boolean isActive;
    private UUID lastWorkspaceId;

    public static UserDto fromEntity(User user) {
        return UserDto.builder()
                .id(user.getId())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .email(user.getEmail())
                .givenName(user.getGivenName())
                .familyName(user.getFamilyName())
                .isActive(user.getIsActive())
                .lastWorkspaceId(user.getLastWorkspaceId())
                .build();
    }

    public static List<UserDto> fromEntities(List<User> users) {
        return users.stream().map(UserDto::fromEntity).toList();
    }
}
