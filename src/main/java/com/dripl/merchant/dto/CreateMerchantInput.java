package com.dripl.merchant.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMerchantInput {

    @Size(min = 1, max = 100, message = "Merchant name must be between 1 and 100 characters")
    private String name;
}
