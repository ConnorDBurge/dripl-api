package com.dripl.workspace.settings;

import com.dripl.workspace.settings.controller.WorkspaceSettingsController;
import com.dripl.workspace.settings.dto.UpdateWorkspaceSettingsDto;
import com.dripl.workspace.settings.dto.WorkspaceSettingsDto;
import com.dripl.workspace.settings.service.WorkspaceSettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceSettingsControllerTest {

    @Mock private WorkspaceSettingsService settingsService;
    @InjectMocks private WorkspaceSettingsController controller;

    private final UUID workspaceId = UUID.randomUUID();

    @Test
    void getSettings_returnsOk() {
        WorkspaceSettingsDto dto = WorkspaceSettingsDto.builder().workspaceId(workspaceId).build();
        when(settingsService.getSettings(workspaceId)).thenReturn(dto);

        ResponseEntity<WorkspaceSettingsDto> response = controller.getSettings(workspaceId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(dto);
    }

    @Test
    void updateSettings_returnsOk() {
        UpdateWorkspaceSettingsDto updateDto = new UpdateWorkspaceSettingsDto();
        WorkspaceSettingsDto dto = WorkspaceSettingsDto.builder().workspaceId(workspaceId).build();
        when(settingsService.updateSettings(workspaceId, updateDto)).thenReturn(dto);

        ResponseEntity<WorkspaceSettingsDto> response = controller.updateSettings(workspaceId, updateDto);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(settingsService).updateSettings(workspaceId, updateDto);
    }
}
