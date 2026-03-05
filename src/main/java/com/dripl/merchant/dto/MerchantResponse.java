package com.dripl.merchant.dto;

import com.dripl.common.dto.BaseDto;
import com.dripl.common.enums.Status;
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
public class MerchantResponse extends BaseDto {

    private UUID workspaceId;
    private String name;
    private Status status;
}
