package com.dripl.tag.controller;

import com.dripl.common.annotation.WorkspaceId;
import com.dripl.tag.dto.CreateTagDto;
import com.dripl.tag.dto.TagDto;
import com.dripl.tag.dto.UpdateTagDto;
import com.dripl.tag.entity.Tag;
import com.dripl.tag.mapper.TagMapper;
import com.dripl.tag.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagService tagService;
    private final TagMapper tagMapper;

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TagDto>> listTags(@WorkspaceId UUID workspaceId) {
        List<Tag> tags = tagService.listAllByWorkspaceId(workspaceId);
        return ResponseEntity.ok(tagMapper.toDtos(tags));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TagDto> createTag(
            @WorkspaceId UUID workspaceId, @Valid @RequestBody CreateTagDto dto) {
        Tag tag = tagService.createTag(workspaceId, dto);
        return ResponseEntity.status(201).body(tagMapper.toDto(tag));
    }

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/{tagId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TagDto> getTag(
            @WorkspaceId UUID workspaceId, @PathVariable UUID tagId) {
        Tag tag = tagService.getTag(tagId, workspaceId);
        return ResponseEntity.ok(tagMapper.toDto(tag));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(value = "/{tagId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TagDto> updateTag(
            @PathVariable UUID tagId,
            @WorkspaceId UUID workspaceId,
            @Valid @RequestBody UpdateTagDto dto) {
        Tag tag = tagService.updateTag(tagId, workspaceId, dto);
        return ResponseEntity.ok(tagMapper.toDto(tag));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> deleteTag(
            @WorkspaceId UUID workspaceId, @PathVariable UUID tagId) {
        tagService.deleteTag(tagId, workspaceId);
        return ResponseEntity.noContent().build();
    }
}
