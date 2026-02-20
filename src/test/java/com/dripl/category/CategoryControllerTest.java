package com.dripl.category;

import com.dripl.category.controller.CategoryController;
import com.dripl.category.dto.CreateCategoryDto;
import com.dripl.category.dto.UpdateCategoryDto;
import com.dripl.category.entity.Category;
import com.dripl.category.mapper.CategoryMapper;
import com.dripl.category.service.CategoryService;
import com.dripl.common.enums.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    @Spy
    private CategoryMapper categoryMapper = Mappers.getMapper(CategoryMapper.class);

    @InjectMocks
    private CategoryController categoryController;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    private Category buildCategory(String name) {
        return Category.builder()
                .id(categoryId)
                .workspaceId(workspaceId)
                .name(name)
                .status(Status.ACTIVE)
                .build();
    }

    @Test
    void listCategories_returns200() {
        when(categoryService.listAll(any(Specification.class))).thenReturn(List.of(buildCategory("Food")));

        var response = categoryController.listCategories(workspaceId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void listCategories_empty_returns200() {
        when(categoryService.listAll(any(Specification.class))).thenReturn(List.of());

        var response = categoryController.listCategories(workspaceId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getCategoryTree_returns200() {
        Category parent = buildCategory("Food");
        Category child = Category.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .parentId(categoryId)
                .name("Groceries")
                .status(Status.ACTIVE)
                .build();
        when(categoryService.listAll(any(Specification.class))).thenReturn(List.of(parent, child));

        var response = categoryController.getCategoryTree(workspaceId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getName()).isEqualTo("Food");
        assertThat(response.getBody().get(0).isGroup()).isTrue();
        assertThat(response.getBody().get(0).getChildren()).hasSize(1);
        assertThat(response.getBody().get(0).getChildren().get(0).getName()).isEqualTo("Groceries");
    }

    @Test
    void getCategory_returns200_withChildren() {
        Category parent = buildCategory("Food");
        Category child = Category.builder()
                .id(UUID.randomUUID())
                .workspaceId(workspaceId)
                .parentId(categoryId)
                .name("Groceries")
                .status(Status.ACTIVE)
                .build();
        when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(parent);
        when(categoryService.getChildren(categoryId)).thenReturn(List.of(child));

        var response = categoryController.getCategory(workspaceId, categoryId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Food");
        assertThat(response.getBody().getChildren()).hasSize(1);
        assertThat(response.getBody().getChildren().get(0).getName()).isEqualTo("Groceries");
    }

    @Test
    void getCategory_returns200_noChildren() {
        when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(buildCategory("Bills"));
        when(categoryService.getChildren(categoryId)).thenReturn(List.of());

        var response = categoryController.getCategory(workspaceId, categoryId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Bills");
        assertThat(response.getBody().getChildren()).isEmpty();
    }

    @Test
    void createCategory_returns201() {
        CreateCategoryDto dto = CreateCategoryDto.builder()
                .name("Food")
                .build();
        when(categoryService.createCategory(eq(workspaceId), any(CreateCategoryDto.class)))
                .thenReturn(buildCategory("Food"));

        var response = categoryController.createCategory(workspaceId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getName()).isEqualTo("Food");
    }

    @Test
    void updateCategory_returns200() {
        UpdateCategoryDto dto = UpdateCategoryDto.builder().name("Updated").build();
        Category updated = buildCategory("Updated");
        when(categoryService.updateCategory(eq(categoryId), eq(workspaceId), any(UpdateCategoryDto.class)))
                .thenReturn(updated);

        var response = categoryController.updateCategory(categoryId, workspaceId, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo("Updated");
    }

    @Test
    void deleteCategory_returns204() {
        var response = categoryController.deleteCategory(workspaceId, categoryId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(categoryService).deleteCategory(categoryId, workspaceId);
    }
}
