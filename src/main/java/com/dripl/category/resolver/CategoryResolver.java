package com.dripl.category.resolver;

import com.dripl.category.dto.CategoryResponse;
import com.dripl.category.dto.CategoryTreeDto;
import com.dripl.category.dto.CreateCategoryInput;
import com.dripl.category.dto.UpdateCategoryInput;
import com.dripl.category.entity.Category;
import com.dripl.category.mapper.CategoryMapper;
import com.dripl.category.repository.CategorySpecifications;
import com.dripl.category.service.CategoryService;
import com.dripl.common.graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.dripl.category.repository.CategorySpecifications.optionally;

@Controller
@RequiredArgsConstructor
public class CategoryResolver {

    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public List<CategoryResponse> categories(@Argument Boolean income) {
        UUID workspaceId = GraphQLContext.workspaceId();
        Specification<Category> spec = Specification
                .where(CategorySpecifications.inWorkspace(workspaceId))
                .and(optionally(income, CategorySpecifications::isIncome));
        return categoryMapper.toDtos(categoryService.listAll(spec));
    }

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public List<CategoryTreeDto> categoryTree() {
        UUID workspaceId = GraphQLContext.workspaceId();
        Specification<Category> spec = Specification.where(CategorySpecifications.inWorkspace(workspaceId));
        List<Category> categories = categoryService.listAll(spec);
        return CategoryTreeDto.buildTree(categories);
    }

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public CategoryResponse category(@Argument UUID id) {
        UUID workspaceId = GraphQLContext.workspaceId();
        Category cat = categoryService.getCategory(id, workspaceId);
        List<Category> children = categoryService.getChildren(id);
        return categoryMapper.toDto(cat)
                .toBuilder()
                .children(categoryMapper.toDtos(children))
                .build();
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public CategoryResponse createCategory(@Argument @Valid CreateCategoryInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return categoryMapper.toDto(categoryService.createCategory(workspaceId, input));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    @SuppressWarnings("unchecked")
    public CategoryResponse updateCategory(@Argument UUID id, @Argument @Valid UpdateCategoryInput input,
                                           DataFetchingEnvironment env) {
        UUID workspaceId = GraphQLContext.workspaceId();
        // GraphQL argument binder doesn't call setter for null values,
        // so check raw input to detect explicit parentId: null
        Map<String, Object> rawInput = env.getArgument("input");
        if (rawInput != null && rawInput.containsKey("parentId") && !input.isParentIdSpecified()) {
            input.setParentId(null);
        }
        return categoryMapper.toDto(categoryService.updateCategory(id, workspaceId, input));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public boolean moveCategory(@Argument UUID id, @Argument int displayOrder) {
        UUID workspaceId = GraphQLContext.workspaceId();
        categoryService.moveCategory(id, workspaceId, displayOrder);
        return true;
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @MutationMapping
    public boolean deleteCategory(@Argument UUID id) {
        UUID workspaceId = GraphQLContext.workspaceId();
        categoryService.deleteCategory(id, workspaceId);
        return true;
    }
}
