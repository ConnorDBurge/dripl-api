package com.dripl.merchant.dto;

import com.dripl.merchant.enums.MerchantStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateMerchantDto {

    @Size(min = 1, max = 100, message = "Merchant name must be between 1 and 100 characters")
    private String name;

    private MerchantStatus status;
}
