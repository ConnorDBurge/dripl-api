package com.dripl.workspace.membership.dto;

import com.dripl.workspace.membership.enums.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMembershipDto {

    @NotEmpty(message = "At least one role must be provided")
    private Set<@NotNull Role> roles;
}
