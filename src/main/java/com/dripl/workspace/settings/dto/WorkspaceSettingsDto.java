package com.dripl.workspace.settings.dto;

import com.dripl.account.enums.CurrencyCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceSettingsDto {

    private UUID id;
    private UUID workspaceId;
    private CurrencyCode defaultCurrencyCode;
    private String timezone;
}
