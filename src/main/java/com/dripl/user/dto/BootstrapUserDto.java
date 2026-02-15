package com.dripl.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BootstrapUserDto {

    @NotBlank(message = "Email must be provided")
    @Email(message = "Email is not valid")
    private String email;

    private String givenName;
    private String familyName;
}
