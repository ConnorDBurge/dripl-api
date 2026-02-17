package com.dripl.tag;

import com.dripl.common.exception.ConflictException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.tag.dto.CreateTagDto;
import com.dripl.tag.dto.UpdateTagDto;
import com.dripl.tag.entity.Tag;
import com.dripl.common.enums.Status;
import com.dripl.tag.mapper.TagMapper;
import com.dripl.tag.repository.TagRepository;
import com.dripl.tag.service.TagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Spy
    private TagMapper tagMapper = Mappers.getMapper(TagMapper.class);

    @InjectMocks
    private TagService tagService;

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

    // --- listAllByWorkspaceId ---

    @Test
    void listAllByWorkspaceId_returnsTags() {
        Tag tag = buildTag("groceries");
        when(tagRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(tag));

        List<Tag> result = tagService.listAllByWorkspaceId(workspaceId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("groceries");
    }

    @Test
    void listAllByWorkspaceId_emptyList() {
        when(tagRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of());

        List<Tag> result = tagService.listAllByWorkspaceId(workspaceId);

        assertThat(result).isEmpty();
    }

    // --- getTag ---

    @Test
    void getTag_found() {
        Tag tag = buildTag("groceries");
        when(tagRepository.findByIdAndWorkspaceId(tagId, workspaceId)).thenReturn(Optional.of(tag));

        Tag result = tagService.getTag(tagId, workspaceId);

        assertThat(result.getName()).isEqualTo("groceries");
    }

    @Test
    void getTag_notFound_throws() {
        when(tagRepository.findByIdAndWorkspaceId(tagId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.getTag(tagId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Tag not found");
    }

    // --- createTag ---

    @Test
    void createTag_success() {
        CreateTagDto dto = CreateTagDto.builder()
                .name("groceries")
                .description("Grocery store purchases")
                .build();

        when(tagRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, "groceries")).thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        Tag result = tagService.createTag(workspaceId, dto);

        assertThat(result.getName()).isEqualTo("groceries");
        assertThat(result.getDescription()).isEqualTo("Grocery store purchases");
        assertThat(result.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(result.getWorkspaceId()).isEqualTo(workspaceId);
    }

    @Test
    void createTag_withoutDescription_success() {
        CreateTagDto dto = CreateTagDto.builder()
                .name("groceries")
                .build();

        when(tagRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, "groceries")).thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        Tag result = tagService.createTag(workspaceId, dto);

        assertThat(result.getName()).isEqualTo("groceries");
        assertThat(result.getDescription()).isNull();
    }

    @Test
    void createTag_duplicateName_throws() {
        CreateTagDto dto = CreateTagDto.builder()
                .name("groceries")
                .build();

        when(tagRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, "groceries")).thenReturn(true);

        assertThatThrownBy(() -> tagService.createTag(workspaceId, dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");
        verify(tagRepository, never()).save(any());
    }

    // --- updateTag ---

    @Test
    void updateTag_name() {
        Tag tag = buildTag("groceries");
        when(tagRepository.findByIdAndWorkspaceId(tagId, workspaceId)).thenReturn(Optional.of(tag));
        when(tagRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, "food")).thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTagDto dto = UpdateTagDto.builder().name("food").build();
        Tag result = tagService.updateTag(tagId, workspaceId, dto);

        assertThat(result.getName()).isEqualTo("food");
    }

    @Test
    void updateTag_description() {
        Tag tag = buildTag("groceries");
        when(tagRepository.findByIdAndWorkspaceId(tagId, workspaceId)).thenReturn(Optional.of(tag));
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTagDto dto = UpdateTagDto.builder().description("Updated description").build();
        Tag result = tagService.updateTag(tagId, workspaceId, dto);

        assertThat(result.getDescription()).isEqualTo("Updated description");
        assertThat(result.getName()).isEqualTo("groceries");
    }

    @Test
    void updateTag_duplicateName_throws() {
        Tag tag = buildTag("groceries");
        when(tagRepository.findByIdAndWorkspaceId(tagId, workspaceId)).thenReturn(Optional.of(tag));
        when(tagRepository.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, "food")).thenReturn(true);

        UpdateTagDto dto = UpdateTagDto.builder().name("food").build();

        assertThatThrownBy(() -> tagService.updateTag(tagId, workspaceId, dto))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateTag_sameName_allowed() {
        Tag tag = buildTag("groceries");
        when(tagRepository.findByIdAndWorkspaceId(tagId, workspaceId)).thenReturn(Optional.of(tag));
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTagDto dto = UpdateTagDto.builder().name("groceries").build();
        Tag result = tagService.updateTag(tagId, workspaceId, dto);

        assertThat(result.getName()).isEqualTo("groceries");
    }

    @Test
    void updateTag_status() {
        Tag tag = buildTag("groceries");
        when(tagRepository.findByIdAndWorkspaceId(tagId, workspaceId)).thenReturn(Optional.of(tag));
        when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTagDto dto = UpdateTagDto.builder().status(Status.ARCHIVED).build();
        Tag result = tagService.updateTag(tagId, workspaceId, dto);

        assertThat(result.getStatus()).isEqualTo(Status.ARCHIVED);
        assertThat(result.getName()).isEqualTo("groceries");
    }

    @Test
    void updateTag_notFound_throws() {
        when(tagRepository.findByIdAndWorkspaceId(tagId, workspaceId)).thenReturn(Optional.empty());

        UpdateTagDto dto = UpdateTagDto.builder().name("X").build();

        assertThatThrownBy(() -> tagService.updateTag(tagId, workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- deleteTag ---

    @Test
    void deleteTag_success() {
        Tag tag = buildTag("groceries");
        when(tagRepository.findByIdAndWorkspaceId(tagId, workspaceId)).thenReturn(Optional.of(tag));

        tagService.deleteTag(tagId, workspaceId);

        verify(tagRepository).delete(tag);
    }

    @Test
    void deleteTag_notFound_throws() {
        when(tagRepository.findByIdAndWorkspaceId(tagId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tagService.deleteTag(tagId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(tagRepository, never()).delete(any());
    }
}
