package com.dripl.category.controller;

import com.dripl.category.dto.CategoryDto;
import com.dripl.category.dto.CategoryTreeDto;
import com.dripl.category.dto.CreateCategoryDto;
import com.dripl.category.dto.UpdateCategoryDto;
import com.dripl.category.entity.Category;
import com.dripl.category.mapper.CategoryMapper;
import com.dripl.category.repository.CategorySpecifications;
import com.dripl.category.service.CategoryService;
import com.dripl.common.annotation.WorkspaceId;
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
import org.springframework.web.bind.annotation.RequestParam;

import static com.dripl.category.repository.CategorySpecifications.optionally;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/tree", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<CategoryTreeDto>> getCategoryTree(@WorkspaceId UUID workspaceId) {
        Specification<Category> spec = Specification.where(CategorySpecifications.inWorkspace(workspaceId));
        List<Category> categories = categoryService.listAll(spec);
        return ResponseEntity.ok(CategoryTreeDto.buildTree(categories));
    }

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<CategoryDto>> listCategories(
            @WorkspaceId UUID workspaceId,
            @RequestParam(required = false) Boolean income) {

        Specification<Category> spec = Specification
                .where(CategorySpecifications.inWorkspace(workspaceId))
                .and(optionally(income, CategorySpecifications::isIncome));

        List<Category> categories = categoryService.listAll(spec);
        return ResponseEntity.ok(categoryMapper.toDtos(categories));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CategoryDto> createCategory(
            @WorkspaceId UUID workspaceId, @Valid @RequestBody CreateCategoryDto dto) {
        Category category = categoryService.createCategory(workspaceId, dto);
        return ResponseEntity.status(201).body(categoryMapper.toDto(category));
    }

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/{categoryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CategoryDto> getCategory(
            @WorkspaceId UUID workspaceId, @PathVariable UUID categoryId) {
        Category category = categoryService.getCategory(categoryId, workspaceId);
        List<Category> children = categoryService.getChildren(categoryId);
        CategoryDto dto = categoryMapper.toDto(category)
                .toBuilder()
                .children(categoryMapper.toDtos(children))
                .build();
        return ResponseEntity.ok(dto);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(value = "/{categoryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CategoryDto> updateCategory(
            @PathVariable UUID categoryId,
            @WorkspaceId UUID workspaceId,
            @Valid @RequestBody UpdateCategoryDto dto) {
        Category category = categoryService.updateCategory(categoryId, workspaceId, dto);
        return ResponseEntity.ok(categoryMapper.toDto(category));
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteCategory(
            @WorkspaceId UUID workspaceId, @PathVariable UUID categoryId) {
        categoryService.deleteCategory(categoryId, workspaceId);
        return ResponseEntity.noContent().build();
    }
}
