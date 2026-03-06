package com.dripl.tag.resolver;

import com.dripl.common.graphql.GraphQLContext;
import com.dripl.tag.dto.CreateTagInput;
import com.dripl.tag.dto.TagResponse;
import com.dripl.tag.dto.UpdateTagInput;
import com.dripl.tag.mapper.TagMapper;
import com.dripl.tag.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class TagResolver {

    private final TagService tagService;
    private final TagMapper tagMapper;

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public List<TagResponse> tags() {
        UUID workspaceId = GraphQLContext.workspaceId();
        return tagMapper.toDtos(tagService.listAllByWorkspaceId(workspaceId));
    }

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public TagResponse tag(@Argument UUID tagId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return tagMapper.toDto(tagService.getTag(tagId, workspaceId));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public TagResponse createTag(@Argument @Valid CreateTagInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return tagMapper.toDto(tagService.createTag(workspaceId, input));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public TagResponse updateTag(@Argument UUID tagId, @Argument @Valid UpdateTagInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return tagMapper.toDto(tagService.updateTag(tagId, workspaceId, input));
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @MutationMapping
    public boolean deleteTag(@Argument UUID tagId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        tagService.deleteTag(tagId, workspaceId);
        return true;
    }
}
