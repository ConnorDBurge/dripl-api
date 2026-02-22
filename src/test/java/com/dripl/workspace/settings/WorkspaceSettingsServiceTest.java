package com.dripl.workspace.settings;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.exception.BadRequestException;
import com.dripl.workspace.settings.dto.UpdateWorkspaceSettingsDto;
import com.dripl.workspace.settings.dto.WorkspaceSettingsDto;
import com.dripl.workspace.settings.entity.WorkspaceSettings;
import com.dripl.workspace.settings.mapper.WorkspaceSettingsMapper;
import com.dripl.workspace.settings.repository.WorkspaceSettingsRepository;
import com.dripl.workspace.settings.service.WorkspaceSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceSettingsServiceTest {

    @Mock private WorkspaceSettingsRepository settingsRepository;
    @Mock private WorkspaceSettingsMapper settingsMapper;
    @InjectMocks private WorkspaceSettingsService service;

    private final UUID workspaceId = UUID.randomUUID();

    @Nested
    class GetSettings {

        @Test
        void getSettings_existing_returnsDto() {
            WorkspaceSettings settings = WorkspaceSettings.builder()
                    .workspaceId(workspaceId)
                    .build();
            WorkspaceSettingsDto dto = WorkspaceSettingsDto.builder()
                    .workspaceId(workspaceId)
                    .build();

            when(settingsRepository.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(settings));
            when(settingsMapper.toDto(settings)).thenReturn(dto);

            WorkspaceSettingsDto result = service.getSettings(workspaceId);
            assertThat(result.getWorkspaceId()).isEqualTo(workspaceId);
        }

        @Test
        void getSettings_notExists_autoProvisions() {
            WorkspaceSettings newSettings = WorkspaceSettings.builder()
                    .workspaceId(workspaceId)
                    .build();
            WorkspaceSettingsDto dto = WorkspaceSettingsDto.builder()
                    .workspaceId(workspaceId)
                    .build();

            when(settingsRepository.findByWorkspaceId(workspaceId)).thenReturn(Optional.empty());
            when(settingsRepository.save(any(WorkspaceSettings.class))).thenReturn(newSettings);
            when(settingsMapper.toDto(newSettings)).thenReturn(dto);

            WorkspaceSettingsDto result = service.getSettings(workspaceId);
            assertThat(result.getWorkspaceId()).isEqualTo(workspaceId);
            verify(settingsRepository).save(any(WorkspaceSettings.class));
        }
    }

    @Nested
    class UpdateSettings {

        private WorkspaceSettings existingSettings;

        @BeforeEach
        void setUp() {
            existingSettings = WorkspaceSettings.builder()
                    .workspaceId(workspaceId)
                    .build();
        }

        @Test
        void updateSettings_currency() {
            UpdateWorkspaceSettingsDto dto = UpdateWorkspaceSettingsDto.builder()
                    .defaultCurrencyCode(CurrencyCode.EUR)
                    .build();

            when(settingsRepository.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(existingSettings));
            when(settingsRepository.save(any())).thenReturn(existingSettings);
            when(settingsMapper.toDto(any())).thenReturn(WorkspaceSettingsDto.builder().build());

            service.updateSettings(workspaceId, dto);

            ArgumentCaptor<WorkspaceSettings> captor = ArgumentCaptor.forClass(WorkspaceSettings.class);
            verify(settingsRepository).save(captor.capture());
            assertThat(captor.getValue().getDefaultCurrencyCode()).isEqualTo(CurrencyCode.EUR);
        }

        @Test
        void updateSettings_timezone_valid() {
            UpdateWorkspaceSettingsDto dto = UpdateWorkspaceSettingsDto.builder()
                    .timezone("America/Chicago")
                    .build();

            when(settingsRepository.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(existingSettings));
            when(settingsRepository.save(any())).thenReturn(existingSettings);
            when(settingsMapper.toDto(any())).thenReturn(WorkspaceSettingsDto.builder().build());

            service.updateSettings(workspaceId, dto);

            ArgumentCaptor<WorkspaceSettings> captor = ArgumentCaptor.forClass(WorkspaceSettings.class);
            verify(settingsRepository).save(captor.capture());
            assertThat(captor.getValue().getTimezone()).isEqualTo("America/Chicago");
        }

        @Test
        void updateSettings_timezone_invalid_throws() {
            UpdateWorkspaceSettingsDto dto = UpdateWorkspaceSettingsDto.builder()
                    .timezone("Invalid/Timezone")
                    .build();

            when(settingsRepository.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(existingSettings));

            assertThatThrownBy(() -> service.updateSettings(workspaceId, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid timezone");
        }
    }
}
