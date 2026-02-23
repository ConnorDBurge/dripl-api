package com.dripl.category;

import com.dripl.budget.repository.BudgetPeriodEntryRepository;
import com.dripl.category.dto.CreateCategoryDto;
import com.dripl.category.dto.UpdateCategoryDto;
import com.dripl.category.entity.Category;
import com.dripl.category.mapper.CategoryMapper;
import com.dripl.category.repository.CategoryRepository;
import com.dripl.category.service.CategoryService;
import com.dripl.common.enums.Status;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

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
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BudgetPeriodEntryRepository budgetPeriodEntryRepository;

    @Spy
    private CategoryMapper categoryMapper = Mappers.getMapper(CategoryMapper.class);

    @InjectMocks
    private CategoryService categoryService;

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

    private List<Category> dummyList(int size) {
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(i -> Category.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                        .name("dummy-" + i).status(Status.ACTIVE).displayOrder(i).build())
                .collect(java.util.stream.Collectors.toList());
    }

    // --- listAll ---

    @Test
    void listAll_returnsCategories() {
        Category category = buildCategory("Food");
        when(categoryRepository.findAll(any(Specification.class))).thenReturn(List.of(category));

        List<Category> result = categoryService.listAll(Specification.where(null));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Food");
    }

    @Test
    void listAll_emptyList() {
        when(categoryRepository.findAll(any(Specification.class))).thenReturn(List.of());

        List<Category> result = categoryService.listAll(Specification.where(null));

        assertThat(result).isEmpty();
    }

    // --- getCategory ---

    @Test
    void getCategory_found() {
        Category category = buildCategory("Food");
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(category));

        Category result = categoryService.getCategory(categoryId, workspaceId);

        assertThat(result.getName()).isEqualTo("Food");
    }

    @Test
    void getCategory_notFound_throws() {
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategory(categoryId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Category not found");
    }

    // --- createCategory ---

    @Test
    void createCategory_rootCategory_success() {
        CreateCategoryDto dto = CreateCategoryDto.builder()
                .name("Food")
                .build();

        when(categoryRepository.findRootsByWorkspaceIdOrderByDisplayOrder(workspaceId)).thenReturn(List.of());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = categoryService.createCategory(workspaceId, dto);

        assertThat(result.getName()).isEqualTo("Food");
        assertThat(result.getStatus()).isEqualTo(Status.ACTIVE);
        assertThat(result.getParentId()).isNull();
        assertThat(result.isIncome()).isFalse();
        assertThat(result.isExcludeFromBudget()).isFalse();
        assertThat(result.isExcludeFromTotals()).isFalse();
        assertThat(result.getDisplayOrder()).isEqualTo(0);
        verify(budgetPeriodEntryRepository, never()).deleteByCategoryId(any());
    }

    @Test
    void createCategory_withAllFields_success() {
        CreateCategoryDto dto = CreateCategoryDto.builder()
                .name("Salary")
                .description("Monthly pay")
                .income(true)
                .excludeFromBudget(true)
                .excludeFromTotals(false)
                .build();

        when(categoryRepository.findRootsByWorkspaceIdOrderByDisplayOrder(workspaceId)).thenReturn(dummyList(3));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = categoryService.createCategory(workspaceId, dto);

        assertThat(result.getName()).isEqualTo("Salary");
        assertThat(result.getDescription()).isEqualTo("Monthly pay");
        assertThat(result.isIncome()).isTrue();
        assertThat(result.isExcludeFromBudget()).isTrue();
        assertThat(result.isExcludeFromTotals()).isFalse();
        assertThat(result.getDisplayOrder()).isEqualTo(3);
    }

    @Test
    void createCategory_withValidParent_success() {
        UUID parentId = UUID.randomUUID();
        Category parent = Category.builder()
                .id(parentId)
                .workspaceId(workspaceId)
                .name("Food")
                .status(Status.ACTIVE)
                .parentId(null)
                .build();

        CreateCategoryDto dto = CreateCategoryDto.builder()
                .name("Groceries")
                .parentId(parentId)
                .build();

        when(categoryRepository.findByIdAndWorkspaceId(parentId, workspaceId)).thenReturn(Optional.of(parent));
        when(categoryRepository.findByParentIdOrderByDisplayOrder(parentId)).thenReturn(List.of());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = categoryService.createCategory(workspaceId, dto);

        assertThat(result.getName()).isEqualTo("Groceries");
        assertThat(result.getParentId()).isEqualTo(parentId);
        assertThat(result.isIncome()).isFalse();
        assertThat(result.getDisplayOrder()).isEqualTo(0);
        verify(budgetPeriodEntryRepository).deleteByCategoryId(parentId);
    }

    @Test
    void createCategory_withParent_inheritsIncomeFromParent_trueToTrue() {
        UUID parentId = UUID.randomUUID();
        Category parent = Category.builder()
                .id(parentId)
                .workspaceId(workspaceId)
                .name("Salary")
                .status(Status.ACTIVE)
                .income(true)
                .parentId(null)
                .build();

        CreateCategoryDto dto = CreateCategoryDto.builder()
                .name("Bonus")
                .parentId(parentId)
                .income(false)
                .build();

        when(categoryRepository.findByIdAndWorkspaceId(parentId, workspaceId)).thenReturn(Optional.of(parent));
        when(categoryRepository.findByParentIdOrderByDisplayOrder(parentId)).thenReturn(List.of());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = categoryService.createCategory(workspaceId, dto);

        assertThat(result.isIncome()).isTrue();
    }

    @Test
    void createCategory_withParent_inheritsIncomeFromParent_falseToFalse() {
        UUID parentId = UUID.randomUUID();
        Category parent = Category.builder()
                .id(parentId)
                .workspaceId(workspaceId)
                .name("Food")
                .status(Status.ACTIVE)
                .income(false)
                .parentId(null)
                .build();

        CreateCategoryDto dto = CreateCategoryDto.builder()
                .name("Groceries")
                .parentId(parentId)
                .income(true)
                .build();

        when(categoryRepository.findByIdAndWorkspaceId(parentId, workspaceId)).thenReturn(Optional.of(parent));
        when(categoryRepository.findByParentIdOrderByDisplayOrder(parentId)).thenReturn(List.of());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category result = categoryService.createCategory(workspaceId, dto);

        assertThat(result.isIncome()).isFalse();
    }

    @Test
    void createCategory_parentNotFound_throws() {
        UUID parentId = UUID.randomUUID();
        CreateCategoryDto dto = CreateCategoryDto.builder()
                .name("Groceries")
                .parentId(parentId)
                .build();

        when(categoryRepository.findByIdAndWorkspaceId(parentId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.createCategory(workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Parent category not found");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createCategory_parentIsChild_throws() {
        UUID parentId = UUID.randomUUID();
        UUID grandparentId = UUID.randomUUID();
        Category parent = Category.builder()
                .id(parentId)
                .workspaceId(workspaceId)
                .name("Groceries")
                .status(Status.ACTIVE)
                .parentId(grandparentId)
                .build();

        CreateCategoryDto dto = CreateCategoryDto.builder()
                .name("Organic")
                .parentId(parentId)
                .build();

        when(categoryRepository.findByIdAndWorkspaceId(parentId, workspaceId)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> categoryService.createCategory(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Parent category cannot have its own parent (maximum depth is 2)");
        verify(categoryRepository, never()).save(any());
    }

    // --- updateCategory ---

    @Test
    void updateCategory_name() {
        Category category = buildCategory("Food");
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCategoryDto dto = UpdateCategoryDto.builder().name("Food & Drink").build();
        Category result = categoryService.updateCategory(categoryId, workspaceId, dto);

        assertThat(result.getName()).isEqualTo("Food & Drink");
    }

    @Test
    void updateCategory_setParent_success() {
        UUID parentId = UUID.randomUUID();
        Category category = buildCategory("Groceries");
        Category parent = Category.builder()
                .id(parentId)
                .workspaceId(workspaceId)
                .name("Food")
                .status(Status.ACTIVE)
                .parentId(null)
                .build();

        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(category));
        when(categoryRepository.findByIdAndWorkspaceId(parentId, workspaceId)).thenReturn(Optional.of(parent));
        when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(false);
        when(categoryRepository.findByParentIdOrderByDisplayOrder(parentId)).thenReturn(List.of());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCategoryDto dto = UpdateCategoryDto.builder().build();
        dto.assignParentId(parentId);
        Category result = categoryService.updateCategory(categoryId, workspaceId, dto);

        assertThat(result.getParentId()).isEqualTo(parentId);
        assertThat(result.isIncome()).isFalse();
        assertThat(result.getDisplayOrder()).isEqualTo(0);
        verify(budgetPeriodEntryRepository).deleteByCategoryId(parentId);
    }

    @Test
    void updateCategory_setParent_inheritsIncome_falseToTrue() {
        UUID parentId = UUID.randomUUID();
        Category category = buildCategory("Bonus");
        category.setIncome(false);

        Category parent = Category.builder()
                .id(parentId)
                .workspaceId(workspaceId)
                .name("Income")
                .status(Status.ACTIVE)
                .income(true)
                .parentId(null)
                .build();

        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(category));
        when(categoryRepository.findByIdAndWorkspaceId(parentId, workspaceId)).thenReturn(Optional.of(parent));
        when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(false);
        when(categoryRepository.findByParentIdOrderByDisplayOrder(parentId)).thenReturn(List.of());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCategoryDto dto = UpdateCategoryDto.builder().build();
        dto.assignParentId(parentId);
        Category result = categoryService.updateCategory(categoryId, workspaceId, dto);

        assertThat(result.getParentId()).isEqualTo(parentId);
        assertThat(result.isIncome()).isTrue();
    }

    @Test
    void updateCategory_setParent_inheritsIncome_trueToFalse() {
        UUID parentId = UUID.randomUUID();
        Category category = buildCategory("Side Gig");
        category.setIncome(true);

        Category parent = Category.builder()
                .id(parentId)
                .workspaceId(workspaceId)
                .name("Expenses")
                .status(Status.ACTIVE)
                .income(false)
                .parentId(null)
                .build();

        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(category));
        when(categoryRepository.findByIdAndWorkspaceId(parentId, workspaceId)).thenReturn(Optional.of(parent));
        when(categoryRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(false);
        when(categoryRepository.findByParentIdOrderByDisplayOrder(parentId)).thenReturn(List.of());
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCategoryDto dto = UpdateCategoryDto.builder().build();
        dto.assignParentId(parentId);
        Category result = categoryService.updateCategory(categoryId, workspaceId, dto);

        assertThat(result.getParentId()).isEqualTo(parentId);
        assertThat(result.isIncome()).isFalse();
    }

    @Test
    void updateCategory_removeParent_success() {
        UUID parentId = UUID.randomUUID();
        Category category = buildCategory("Groceries");
        category.setParentId(parentId);

        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(category));
        when(categoryRepository.findRootsByWorkspaceIdOrderByDisplayOrder(workspaceId)).thenReturn(dummyList(4));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCategoryDto dto = UpdateCategoryDto.builder().build();
        dto.assignParentId(null);
        Category result = categoryService.updateCategory(categoryId, workspaceId, dto);

        assertThat(result.getParentId()).isNull();
        assertThat(result.getDisplayOrder()).isEqualTo(4);
    }

    @Test
    void updateCategory_parentIdNotSpecified_doesNotChangeParent() {
        UUID existingParentId = UUID.randomUUID();
        Category category = buildCategory("Groceries");
        category.setParentId(existingParentId);

        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCategoryDto dto = UpdateCategoryDto.builder().name("Grocery Store").build();
        Category result = categoryService.updateCategory(categoryId, workspaceId, dto);

        assertThat(result.getName()).isEqualTo("Grocery Store");
        assertThat(result.getParentId()).isEqualTo(existingParentId);
    }

    @Test
    void updateCategory_selfParent_throws() {
        Category category = buildCategory("Food");
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(category));

        UpdateCategoryDto dto = UpdateCategoryDto.builder().build();
        dto.assignParentId(categoryId);

        assertThatThrownBy(() -> categoryService.updateCategory(categoryId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Category cannot be its own parent");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_parentNotFound_throws() {
        UUID fakeParentId = UUID.randomUUID();
        Category category = buildCategory("Groceries");

        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(category));
        when(categoryRepository.findByIdAndWorkspaceId(fakeParentId, workspaceId)).thenReturn(Optional.empty());

        UpdateCategoryDto dto = UpdateCategoryDto.builder().build();
        dto.assignParentId(fakeParentId);

        assertThatThrownBy(() -> categoryService.updateCategory(categoryId, workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Parent category not found");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_parentTooDeep_throws() {
        UUID parentId = UUID.randomUUID();
        UUID grandparentId = UUID.randomUUID();
        Category category = buildCategory("Organic");
        Category parent = Category.builder()
                .id(parentId)
                .workspaceId(workspaceId)
                .name("Groceries")
                .status(Status.ACTIVE)
                .parentId(grandparentId)
                .build();

        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(category));
        when(categoryRepository.findByIdAndWorkspaceId(parentId, workspaceId)).thenReturn(Optional.of(parent));

        UpdateCategoryDto dto = UpdateCategoryDto.builder().build();
        dto.assignParentId(parentId);

        assertThatThrownBy(() -> categoryService.updateCategory(categoryId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Parent category cannot have its own parent (maximum depth is 2)");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_circularReference_throws() {
        // A is parent of B. Try to make B the parent of A.
        UUID childId = UUID.randomUUID();
        Category parentCategory = Category.builder()
                .id(categoryId)
                .workspaceId(workspaceId)
                .name("Food")
                .status(Status.ACTIVE)
                .parentId(null)
                .build();
        Category childCategory = Category.builder()
                .id(childId)
                .workspaceId(workspaceId)
                .name("Groceries")
                .status(Status.ACTIVE)
                .parentId(categoryId)
                .build();

        // We're updating the parent (Food) to set its parent to the child (Groceries)
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(parentCategory));
        when(categoryRepository.findByIdAndWorkspaceId(childId, workspaceId)).thenReturn(Optional.of(childCategory));

        UpdateCategoryDto dto = UpdateCategoryDto.builder().build();
        dto.assignParentId(childId);

        // childCategory.parentId != null → validateParent blocks it as "too deep"
        assertThatThrownBy(() -> categoryService.updateCategory(categoryId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Parent category cannot have its own parent (maximum depth is 2)");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_categoryWithChildren_cannotBeNested() {
        // Food has children (Groceries, Dining). Try to make Food a child of Transport.
        UUID transportId = UUID.randomUUID();
        Category food = buildCategory("Food");
        Category transport = Category.builder()
                .id(transportId)
                .workspaceId(workspaceId)
                .name("Transport")
                .status(Status.ACTIVE)
                .parentId(null)
                .build();

        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(food));
        when(categoryRepository.findByIdAndWorkspaceId(transportId, workspaceId)).thenReturn(Optional.of(transport));
        when(categoryRepository.findById(transportId)).thenReturn(Optional.of(transport));
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(true);

        UpdateCategoryDto dto = UpdateCategoryDto.builder().build();
        dto.assignParentId(transportId);

        assertThatThrownBy(() -> categoryService.updateCategory(categoryId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot nest a category that already has children (maximum depth is 2)");
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateCategory_status() {
        Category category = buildCategory("Food");
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCategoryDto dto = UpdateCategoryDto.builder().status(Status.ARCHIVED).build();
        Category result = categoryService.updateCategory(categoryId, workspaceId, dto);

        assertThat(result.getStatus()).isEqualTo(Status.ARCHIVED);
    }

    @Test
    void updateCategory_notFound_throws() {
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.empty());

        UpdateCategoryDto dto = UpdateCategoryDto.builder().name("X").build();

        assertThatThrownBy(() -> categoryService.updateCategory(categoryId, workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCategory_clearChildren_detachesAll() {
        Category category = buildCategory("Food");
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.findAllByParentId(categoryId)).thenReturn(List.of());
        when(categoryRepository.findRootsByWorkspaceIdOrderByDisplayOrder(workspaceId)).thenReturn(List.of());

        UpdateCategoryDto dto = UpdateCategoryDto.builder().build();
        dto.assignChildren(List.of());

        categoryService.updateCategory(categoryId, workspaceId, dto);

        verify(categoryRepository).findAllByParentId(categoryId);
        verify(categoryRepository).saveAll(any());
    }

    @Test
    void updateCategory_childrenNotSpecified_doesNotDetach() {
        Category category = buildCategory("Food");
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCategoryDto dto = UpdateCategoryDto.builder().name("Food & Drink").build();

        categoryService.updateCategory(categoryId, workspaceId, dto);

        verify(categoryRepository, never()).detachChildren(any());
    }

    @Test
    void updateCategory_clearChildrenAndSetParent_success() {
        // Food has children, clear them and move Food under Transport
        UUID transportId = UUID.randomUUID();
        Category food = buildCategory("Food");
        Category transport = Category.builder()
                .id(transportId)
                .workspaceId(workspaceId)
                .name("Transport")
                .status(Status.ACTIVE)
                .parentId(null)
                .build();

        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(food));
        when(categoryRepository.findByIdAndWorkspaceId(transportId, workspaceId)).thenReturn(Optional.of(transport));
        when(categoryRepository.findById(transportId)).thenReturn(Optional.of(transport));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        when(categoryRepository.findAllByParentId(categoryId)).thenReturn(List.of());
        when(categoryRepository.findRootsByWorkspaceIdOrderByDisplayOrder(workspaceId)).thenReturn(List.of());
        when(categoryRepository.findByParentIdOrderByDisplayOrder(transportId)).thenReturn(List.of());

        UpdateCategoryDto dto = UpdateCategoryDto.builder().build();
        dto.assignChildren(List.of());
        dto.assignParentId(transportId);

        Category result = categoryService.updateCategory(categoryId, workspaceId, dto);

        verify(categoryRepository).findAllByParentId(categoryId);
        assertThat(result.getParentId()).isEqualTo(transportId);
        assertThat(result.getDisplayOrder()).isEqualTo(0);
    }

    // --- deleteCategory ---

    @Test
    void deleteCategory_success() {
        Category category = buildCategory("Food");
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(category));

        categoryService.deleteCategory(categoryId, workspaceId);

        verify(categoryRepository).delete(category);
    }

    @Test
    void deleteCategory_notFound_throws() {
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory(categoryId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(categoryRepository, never()).delete(any(Category.class));
    }

    // --- validateCategoryPolarity ---

    @Test
    void validateCategoryPolarity_positiveAmountWithIncomeCategory_passes() {
        Category income = Category.builder().id(categoryId).workspaceId(workspaceId).name("Salary").income(true).build();
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(income));
        categoryService.validateCategoryPolarity(categoryId, new java.math.BigDecimal("100.00"), workspaceId);
    }

    @Test
    void validateCategoryPolarity_negativeAmountWithExpenseCategory_passes() {
        Category expense = Category.builder().id(categoryId).workspaceId(workspaceId).name("Groceries").income(false).build();
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(expense));
        categoryService.validateCategoryPolarity(categoryId, new java.math.BigDecimal("-50.00"), workspaceId);
    }

    @Test
    void validateCategoryPolarity_zeroAmountWithExpenseCategory_passes() {
        Category expense = Category.builder().id(categoryId).workspaceId(workspaceId).name("Groceries").income(false).build();
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(expense));
        categoryService.validateCategoryPolarity(categoryId, java.math.BigDecimal.ZERO, workspaceId);
    }

    @Test
    void validateCategoryPolarity_positiveAmountWithExpenseCategory_throws() {
        Category expense = Category.builder().id(categoryId).workspaceId(workspaceId).name("Groceries").income(false).build();
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(expense));
        assertThatThrownBy(() -> categoryService.validateCategoryPolarity(categoryId, new java.math.BigDecimal("100.00"), workspaceId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("income category");
    }

    @Test
    void validateCategoryPolarity_negativeAmountWithIncomeCategory_throws() {
        Category income = Category.builder().id(categoryId).workspaceId(workspaceId).name("Salary").income(true).build();
        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(income));
        assertThatThrownBy(() -> categoryService.validateCategoryPolarity(categoryId, new java.math.BigDecimal("-50.00"), workspaceId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expense category");
    }

    @Test
    void validateCategoryPolarity_nullCategoryId_doesNothing() {
        categoryService.validateCategoryPolarity(null, new java.math.BigDecimal("100.00"), workspaceId);
    }

    @Test
    void validateCategoryPolarity_nullAmount_doesNothing() {
        categoryService.validateCategoryPolarity(categoryId, null, workspaceId);
    }

    // --- validateNotGroup ---

    @Test
    void validateNotGroup_leafCategory_succeeds() {
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(false);
        categoryService.validateNotGroup(categoryId);
    }

    @Test
    void validateNotGroup_groupCategory_throws() {
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(true);
        assertThatThrownBy(() -> categoryService.validateNotGroup(categoryId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("parent category group");
    }

    @Test
    void validateNotGroup_nullCategoryId_doesNothing() {
        categoryService.validateNotGroup(null);
        verify(categoryRepository, never()).existsByParentId(any());
    }

    // --- moveCategory ---

    @Test
    void moveCategory_rootToNewPosition_success() {
        UUID cat1Id = UUID.randomUUID();
        UUID cat2Id = UUID.randomUUID();
        UUID cat3Id = UUID.randomUUID();
        Category cat1 = Category.builder().id(cat1Id).workspaceId(workspaceId).name("A").status(Status.ACTIVE).displayOrder(0).build();
        Category cat2 = Category.builder().id(cat2Id).workspaceId(workspaceId).name("B").status(Status.ACTIVE).displayOrder(1).build();
        Category cat3 = Category.builder().id(cat3Id).workspaceId(workspaceId).name("C").status(Status.ACTIVE).displayOrder(2).build();

        when(categoryRepository.findByIdAndWorkspaceId(cat3Id, workspaceId)).thenReturn(Optional.of(cat3));
        when(categoryRepository.findRootsByWorkspaceIdOrderByDisplayOrder(workspaceId))
                .thenReturn(new java.util.ArrayList<>(List.of(cat1, cat2, cat3)));

        categoryService.moveCategory(cat3Id, workspaceId, 0);

        // C moved to front: C=0, A=1, B=2
        assertThat(cat3.getDisplayOrder()).isEqualTo(0);
        assertThat(cat1.getDisplayOrder()).isEqualTo(1);
        assertThat(cat2.getDisplayOrder()).isEqualTo(2);
        verify(categoryRepository).saveAll(any());
    }

    @Test
    void moveCategory_childWithinParent_success() {
        UUID parentId = UUID.randomUUID();
        UUID child1Id = UUID.randomUUID();
        UUID child2Id = UUID.randomUUID();
        UUID child3Id = UUID.randomUUID();
        Category child1 = Category.builder().id(child1Id).workspaceId(workspaceId).parentId(parentId).name("X").status(Status.ACTIVE).displayOrder(0).build();
        Category child2 = Category.builder().id(child2Id).workspaceId(workspaceId).parentId(parentId).name("Y").status(Status.ACTIVE).displayOrder(1).build();
        Category child3 = Category.builder().id(child3Id).workspaceId(workspaceId).parentId(parentId).name("Z").status(Status.ACTIVE).displayOrder(2).build();

        when(categoryRepository.findByIdAndWorkspaceId(child1Id, workspaceId)).thenReturn(Optional.of(child1));
        when(categoryRepository.findByParentIdOrderByDisplayOrder(parentId))
                .thenReturn(new java.util.ArrayList<>(List.of(child1, child2, child3)));

        categoryService.moveCategory(child1Id, workspaceId, 2);

        // X moved to end: Y=0, Z=1, X=2
        assertThat(child2.getDisplayOrder()).isEqualTo(0);
        assertThat(child3.getDisplayOrder()).isEqualTo(1);
        assertThat(child1.getDisplayOrder()).isEqualTo(2);
        verify(categoryRepository).saveAll(any());
    }

    @Test
    void moveCategory_clampsPosition() {
        UUID cat1Id = UUID.randomUUID();
        Category cat1 = Category.builder().id(cat1Id).workspaceId(workspaceId).name("A").status(Status.ACTIVE).displayOrder(0).build();

        when(categoryRepository.findByIdAndWorkspaceId(cat1Id, workspaceId)).thenReturn(Optional.of(cat1));
        when(categoryRepository.findRootsByWorkspaceIdOrderByDisplayOrder(workspaceId))
                .thenReturn(new java.util.ArrayList<>(List.of(cat1)));

        categoryService.moveCategory(cat1Id, workspaceId, 99);

        assertThat(cat1.getDisplayOrder()).isEqualTo(0);
        verify(categoryRepository).saveAll(any());
    }

    @Test
    void moveCategory_notFound_throws() {
        UUID missingId = UUID.randomUUID();
        when(categoryRepository.findByIdAndWorkspaceId(missingId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.moveCategory(missingId, workspaceId, 0))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- detachChildren ordering ---

    @Test
    void updateCategory_clearChildren_childrenBecomeRootsWithOrder() {
        UUID child1Id = UUID.randomUUID();
        UUID child2Id = UUID.randomUUID();
        Category parent = buildCategory("Food");
        Category child1 = Category.builder().id(child1Id).workspaceId(workspaceId).name("Groceries")
                .parentId(categoryId).status(Status.ACTIVE).displayOrder(0).build();
        Category child2 = Category.builder().id(child2Id).workspaceId(workspaceId).name("Dining")
                .parentId(categoryId).status(Status.ACTIVE).displayOrder(1).build();

        when(categoryRepository.findByIdAndWorkspaceId(categoryId, workspaceId)).thenReturn(Optional.of(parent));
        when(categoryRepository.findAllByParentId(categoryId)).thenReturn(List.of(child1, child2));
        when(categoryRepository.findRootsByWorkspaceIdOrderByDisplayOrder(workspaceId)).thenReturn(dummyList(6));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCategoryDto dto = UpdateCategoryDto.builder().build();
        dto.assignChildren(List.of());

        categoryService.updateCategory(categoryId, workspaceId, dto);

        assertThat(child1.getParentId()).isNull();
        assertThat(child1.getDisplayOrder()).isEqualTo(6);
        assertThat(child2.getParentId()).isNull();
        assertThat(child2.getDisplayOrder()).isEqualTo(7);
    }
}
