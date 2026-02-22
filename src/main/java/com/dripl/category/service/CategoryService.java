package com.dripl.category.service;

import com.dripl.budget.repository.BudgetPeriodEntryRepository;
import com.dripl.category.dto.CreateCategoryDto;
import com.dripl.category.dto.UpdateCategoryDto;
import com.dripl.category.entity.Category;
import com.dripl.category.mapper.CategoryMapper;
import com.dripl.category.repository.CategoryRepository;
import com.dripl.common.enums.Status;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final BudgetPeriodEntryRepository budgetPeriodEntryRepository;

    @Transactional(readOnly = true)
    public List<Category> listAll(Specification<Category> spec) {
        return categoryRepository.findAll(spec);
    }

    @Transactional(readOnly = true)
    public Category getCategory(UUID categoryId, UUID workspaceId) {
        return categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    @Transactional(readOnly = true)
    public List<Category> getChildren(UUID parentId) {
        return categoryRepository.findAllByParentId(parentId);
    }

    @Transactional
    public Category createCategory(UUID workspaceId, CreateCategoryDto dto) {
        boolean income = dto.getIncome() != null && dto.getIncome();

        if (dto.getParentId() != null) {
            Category parent = validateParent(workspaceId, dto.getParentId());
            income = parent.isIncome();
            budgetPeriodEntryRepository.deleteByCategoryId(dto.getParentId());
        }

        Category category = Category.builder()
                .workspaceId(workspaceId)
                .parentId(dto.getParentId())
                .name(dto.getName())
                .description(dto.getDescription())
                .status(Status.ACTIVE)
                .income(income)
                .excludeFromBudget(dto.getExcludeFromBudget() != null && dto.getExcludeFromBudget())
                .excludeFromTotals(dto.getExcludeFromTotals() != null && dto.getExcludeFromTotals())
                .build();

        log.info("Created category '{}'", dto.getName());
        return categoryRepository.save(category);
    }

    @Transactional
    public Category updateCategory(UUID categoryId, UUID workspaceId, UpdateCategoryDto dto) {
        Category category = getCategory(categoryId, workspaceId);

        categoryMapper.updateEntity(dto, category);

        if (dto.isChildrenSpecified()) {
            categoryRepository.detachChildren(categoryId);
        }

        if (dto.isParentIdSpecified()) {
            UUID parentId = dto.getParentId();
            if (parentId != null) {
                if (parentId.equals(categoryId)) {
                    throw new BadRequestException("Category cannot be its own parent");
                }
                Category parent = validateParent(workspaceId, parentId);
                ensureNoCircularReference(categoryId, parentId);
                if (!dto.isChildrenSpecified()) {
                    ensureNoCategoryChildren(categoryId);
                }
                category.setIncome(parent.isIncome());
                budgetPeriodEntryRepository.deleteByCategoryId(parentId);
            }
            category.setParentId(parentId);
        }

        log.info("Updating category '{}' ({})", category.getName(), categoryId);
        return categoryRepository.save(category);
    }

    @Transactional
    public void deleteCategory(UUID categoryId, UUID workspaceId) {
        Category category = getCategory(categoryId, workspaceId);
        log.info("Deleting category '{}' ({})", category.getName(), categoryId);
        categoryRepository.delete(category);
    }

    private Category validateParent(UUID workspaceId, UUID parentId) {
        Category parent = categoryRepository.findByIdAndWorkspaceId(parentId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"));

        if (parent.getParentId() != null) {
            throw new BadRequestException("Parent category cannot have its own parent (maximum depth is 2)");
        }

        return parent;
    }

    private void ensureNoCircularReference(UUID categoryId, UUID parentId) {
        Set<UUID> visited = new HashSet<>();
        UUID current = parentId;

        while (current != null) {
            if (!visited.add(current) || categoryId.equals(current)) {
                throw new BadRequestException("Circular category relationship detected");
            }
            current = categoryRepository.findById(current)
                    .map(Category::getParentId)
                    .orElse(null);
        }
    }

    private void ensureNoCategoryChildren(UUID categoryId) {
        if (categoryRepository.existsByParentId(categoryId)) {
            throw new BadRequestException("Cannot nest a category that already has children (maximum depth is 2)");
        }
    }

    /**
     * Validates that a category is not a group (has no children).
     * Transactions can only be assigned to leaf categories.
     */
    @Transactional(readOnly = true)
    public void validateNotGroup(UUID categoryId) {
        if (categoryId != null && categoryRepository.existsByParentId(categoryId)) {
            throw new BadRequestException("Cannot assign a transaction to a parent category group");
        }
    }

    /**
     * Validates that a category's income flag matches the amount sign.
     * Positive amounts require income categories; negative/zero amounts require expense categories.
     */
    @Transactional
    public void validateCategoryPolarity(UUID categoryId, BigDecimal amount, UUID workspaceId) {
        if (categoryId == null || amount == null) return;
        Category category = getCategory(categoryId, workspaceId);
        boolean isPositive = amount.compareTo(BigDecimal.ZERO) > 0;
        if (isPositive && !category.isIncome()) {
            throw new BadRequestException("Positive amounts must use an income category, but '" + category.getName() + "' is an expense category.");
        }
        if (!isPositive && category.isIncome()) {
            throw new BadRequestException("Negative amounts must use an expense category, but '" + category.getName() + "' is an income category.");
        }
    }
}
