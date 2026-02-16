package com.dripl.user.dto;

import com.dripl.common.dto.BaseDto;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

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
}
