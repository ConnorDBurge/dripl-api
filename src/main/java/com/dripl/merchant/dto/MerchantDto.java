package com.dripl.merchant.dto;

import com.dripl.common.dto.BaseDto;
import com.dripl.merchant.enums.MerchantStatus;
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
public class MerchantDto extends BaseDto {

    private UUID workspaceId;
    private String name;
    private MerchantStatus status;
}
