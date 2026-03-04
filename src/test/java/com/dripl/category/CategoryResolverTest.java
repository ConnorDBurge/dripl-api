package com.dripl.category;

import com.dripl.category.dto.CategoryResponse;
import com.dripl.category.dto.CategoryTreeDto;
import com.dripl.category.dto.CreateCategoryInput;
import com.dripl.category.dto.UpdateCategoryInput;
import com.dripl.category.entity.Category;
import com.dripl.category.mapper.CategoryMapper;
import com.dripl.category.resolver.CategoryResolver;
import com.dripl.category.service.CategoryService;
import com.dripl.common.enums.Status;
import graphql.schema.DataFetchingEnvironment;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryResolverTest {

    @Mock
    private CategoryService categoryService;

    @Spy
    private CategoryMapper categoryMapper = Mappers.getMapper(CategoryMapper.class);

    @InjectMocks
    private CategoryResolver categoryResolver;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

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

    private Category buildCategory(String name) {
        return Category.builder()
                .id(categoryId)
                .workspaceId(workspaceId)
                .name(name)
                .status(Status.ACTIVE)
                .income(false)
                .excludeFromBudget(false)
                .excludeFromTotals(false)
                .displayOrder(0)
                .build();
    }

    @Test
    void categories_returnsList() {
        when(categoryService.listAll(any())).thenReturn(List.of(buildCategory("Groceries")));

        List<CategoryResponse> result = categoryResolver.categories(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Groceries");
    }

    @Test
    void categories_withIncomeFilter() {
        when(categoryService.listAll(any())).thenReturn(List.of(buildCategory("Salary")));

        List<CategoryResponse> result = categoryResolver.categories(true);

        assertThat(result).hasSize(1);
    }

    @Test
    void categoryTree_returnsTree() {
        when(categoryService.listAll(any())).thenReturn(List.of(buildCategory("Food")));

        List<CategoryTreeDto> result = categoryResolver.categoryTree();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Food");
    }

    @Test
    void category_returnsWithChildren() {
        when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(buildCategory("Food"));
        when(categoryService.getChildren(categoryId)).thenReturn(List.of());

        CategoryResponse result = categoryResolver.category(categoryId);

        assertThat(result.getName()).isEqualTo("Food");
        assertThat(result.getChildren()).isEmpty();
    }

    @Test
    void createCategory_delegatesToService() {
        CreateCategoryInput input = CreateCategoryInput.builder().name("Dining").build();
        when(categoryService.createCategory(eq(workspaceId), any(CreateCategoryInput.class)))
                .thenReturn(buildCategory("Dining"));

        CategoryResponse result = categoryResolver.createCategory(input);

        assertThat(result.getName()).isEqualTo("Dining");
    }

    @Test
    void updateCategory_delegatesToService() {
        UpdateCategoryInput input = UpdateCategoryInput.builder().name("Updated").build();
        when(categoryService.updateCategory(eq(categoryId), eq(workspaceId), any(UpdateCategoryInput.class)))
                .thenReturn(buildCategory("Updated"));

        DataFetchingEnvironment env = mock(DataFetchingEnvironment.class);
        when(env.getArgument("input")).thenReturn(Map.of("name", "Updated"));

        CategoryResponse result = categoryResolver.updateCategory(categoryId, input, env);

        assertThat(result.getName()).isEqualTo("Updated");
    }

    @Test
    void moveCategory_delegatesToService() {
        boolean result = categoryResolver.moveCategory(categoryId, 3);

        assertThat(result).isTrue();
        verify(categoryService).moveCategory(categoryId, workspaceId, 3);
    }

    @Test
    void deleteCategory_delegatesToService() {
        boolean result = categoryResolver.deleteCategory(categoryId);

        assertThat(result).isTrue();
        verify(categoryService).deleteCategory(categoryId, workspaceId);
    }
}
