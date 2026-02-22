package com.dripl.workspace.settings.service;

import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.workspace.settings.dto.UpdateWorkspaceSettingsDto;
import com.dripl.workspace.settings.dto.WorkspaceSettingsDto;
import com.dripl.workspace.settings.entity.WorkspaceSettings;
import com.dripl.workspace.settings.mapper.WorkspaceSettingsMapper;
import com.dripl.workspace.settings.repository.WorkspaceSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.TimeZone;
import java.util.UUID;

import com.dripl.common.exception.BadRequestException;

@Slf4j
@RequiredArgsConstructor
@Service
public class WorkspaceSettingsService {

    private final WorkspaceSettingsRepository settingsRepository;
    private final WorkspaceSettingsMapper settingsMapper;

    @Transactional(readOnly = true)
    public WorkspaceSettingsDto getSettings(UUID workspaceId) {
        WorkspaceSettings settings = getOrCreateSettings(workspaceId);
        return settingsMapper.toDto(settings);
    }

    @Transactional
    public WorkspaceSettingsDto updateSettings(UUID workspaceId, UpdateWorkspaceSettingsDto dto) {
        WorkspaceSettings settings = getOrCreateSettings(workspaceId);

        if (dto.getDefaultCurrencyCode() != null) {
            settings.setDefaultCurrencyCode(dto.getDefaultCurrencyCode());
        }
        if (dto.getTimezone() != null) {
            validateTimezone(dto.getTimezone());
            settings.setTimezone(dto.getTimezone());
        }

        settings = settingsRepository.save(settings);
        log.info("Updated workspace settings for workspace {}", workspaceId);
        return settingsMapper.toDto(settings);
    }

    @Transactional
    public WorkspaceSettings getOrCreateSettings(UUID workspaceId) {
        return settingsRepository.findByWorkspaceId(workspaceId)
                .orElseGet(() -> {
                    log.info("Auto-provisioning workspace settings for workspace {}", workspaceId);
                    WorkspaceSettings newSettings = WorkspaceSettings.builder()
                            .workspaceId(workspaceId)
                            .build();
                    return settingsRepository.save(newSettings);
                });
    }

    public WorkspaceSettings findByWorkspaceId(UUID workspaceId) {
        return settingsRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Workspace settings not found for workspace " + workspaceId));
    }

    private void validateTimezone(String timezone) {
        String[] available = TimeZone.getAvailableIDs();
        for (String tz : available) {
            if (tz.equals(timezone)) {
                return;
            }
        }
        throw new BadRequestException("Invalid timezone: " + timezone);
    }
}
