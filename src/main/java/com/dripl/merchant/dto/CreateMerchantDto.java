package com.dripl.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMerchantDto {

    @NotBlank(message = "Merchant name must be provided")
    @Size(min = 1, max = 100, message = "Merchant name must be between 1 and 100 characters")
    private String name;
}
