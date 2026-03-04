package com.dripl.tag;

import com.dripl.common.enums.Status;
import com.dripl.tag.dto.CreateTagInput;
import com.dripl.tag.dto.TagResponse;
import com.dripl.tag.dto.UpdateTagInput;
import com.dripl.tag.entity.Tag;
import com.dripl.tag.mapper.TagMapper;
import com.dripl.tag.resolver.TagResolver;
import com.dripl.tag.service.TagService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagResolverTest {

    @Mock
    private TagService tagService;

    @Spy
    private TagMapper tagMapper = Mappers.getMapper(TagMapper.class);

    @InjectMocks
    private TagResolver tagResolver;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID tagId = UUID.randomUUID();

    @BeforeEach
    void setUpSecurityContext() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.get("workspace_id", String.class)).thenReturn(workspaceId.toString());
        var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Tag buildTag(String name) {
        return Tag.builder()
                .id(tagId)
                .workspaceId(workspaceId)
                .name(name)
                .status(Status.ACTIVE)
                .build();
    }

    @Test
    void tags_returnsList() {
        when(tagService.listAllByWorkspaceId(workspaceId))
                .thenReturn(List.of(buildTag("Groceries")));

        List<TagResponse> result = tagResolver.tags();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Groceries");
    }

    @Test
    void tags_emptyList() {
        when(tagService.listAllByWorkspaceId(workspaceId)).thenReturn(List.of());

        List<TagResponse> result = tagResolver.tags();

        assertThat(result).isEmpty();
    }

    @Test
    void tag_returnsById() {
        when(tagService.getTag(tagId, workspaceId)).thenReturn(buildTag("Groceries"));

        TagResponse result = tagResolver.tag(tagId);

        assertThat(result.getName()).isEqualTo("Groceries");
    }

    @Test
    void createTag_delegatesToService() {
        CreateTagInput input = CreateTagInput.builder().name("Travel").build();
        when(tagService.createTag(eq(workspaceId), any(CreateTagInput.class)))
                .thenReturn(buildTag("Travel"));

        TagResponse result = tagResolver.createTag(input);

        assertThat(result.getName()).isEqualTo("Travel");
    }

    @Test
    void updateTag_delegatesToService() {
        UpdateTagInput input = UpdateTagInput.builder().name("Updated").build();
        when(tagService.updateTag(eq(tagId), eq(workspaceId), any(UpdateTagInput.class)))
                .thenReturn(buildTag("Updated"));

        TagResponse result = tagResolver.updateTag(tagId, input);

        assertThat(result.getName()).isEqualTo("Updated");
    }

    @Test
    void deleteTag_delegatesToService() {
        boolean result = tagResolver.deleteTag(tagId);

        assertThat(result).isTrue();
        verify(tagService).deleteTag(tagId, workspaceId);
    }
}
