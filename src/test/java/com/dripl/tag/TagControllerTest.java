package com.dripl.tag;

import com.dripl.tag.controller.TagController;
import com.dripl.tag.dto.CreateTagDto;
import com.dripl.tag.dto.UpdateTagDto;
import com.dripl.tag.entity.Tag;
import com.dripl.common.enums.Status;
import com.dripl.tag.mapper.TagMapper;
import com.dripl.tag.service.TagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagControllerTest {

    @Mock
    private TagService tagService;

    @Spy
    private TagMapper tagMapper = Mappers.getMapper(TagMapper.class);

    @InjectMocks
    private TagController tagController;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID tagId = UUID.randomUUID();

    private Tag buildTag(String name) {
        return Tag.builder()
                .id(tagId)
                .workspaceId(workspaceId)
                .name(name)
                .status(Status.ACTIVE)
                .build();
    }

    @Test
    void listTags_returns200() {
        when(tagService.listAllByWorkspaceId(workspaceId)).thenReturn(List.of(buildTag("groceries")));

        var response = tagController.listTags(workspaceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void listTags_empty_returns200() {
        when(tagService.listAllByWorkspaceId(workspaceId)).thenReturn(List.of());

        var response = tagController.listTags(workspaceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getTag_returns200() {
        when(tagService.getTag(tagId, workspaceId)).thenReturn(buildTag("groceries"));

        var response = tagController.getTag(workspaceId, tagId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("groceries");
    }

    @Test
    void createTag_returns201() {
        CreateTagDto dto = CreateTagDto.builder()
                .name("groceries")
                .build();
        when(tagService.createTag(eq(workspaceId), any(CreateTagDto.class)))
                .thenReturn(buildTag("groceries"));

        var response = tagController.createTag(workspaceId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getName()).isEqualTo("groceries");
    }

    @Test
    void updateTag_returns200() {
        UpdateTagDto dto = UpdateTagDto.builder().name("Updated").build();
        Tag updated = buildTag("Updated");
        when(tagService.updateTag(eq(tagId), eq(workspaceId), any(UpdateTagDto.class)))
                .thenReturn(updated);

        var response = tagController.updateTag(tagId, workspaceId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Updated");
    }

    @Test
    void deleteTag_returns204() {
        var response = tagController.deleteTag(workspaceId, tagId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(tagService).deleteTag(tagId, workspaceId);
    }
}
