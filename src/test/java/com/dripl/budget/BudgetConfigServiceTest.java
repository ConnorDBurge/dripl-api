package com.dripl.budget;

import com.dripl.budget.dto.SetExpectedAmountDto;
import com.dripl.budget.dto.UpdateBudgetCategoryConfigDto;
import com.dripl.budget.entity.Budget;
import com.dripl.budget.entity.BudgetCategoryConfig;
import com.dripl.budget.entity.BudgetPeriodEntry;
import com.dripl.budget.enums.RolloverType;
import com.dripl.budget.repository.BudgetCategoryConfigRepository;
import com.dripl.budget.repository.BudgetPeriodEntryRepository;
import com.dripl.budget.service.BudgetService;
import com.dripl.budget.service.BudgetConfigService;
import com.dripl.category.entity.Category;
import com.dripl.category.repository.CategoryRepository;
import com.dripl.category.service.CategoryService;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetConfigServiceTest {

    @Mock private BudgetCategoryConfigRepository configRepository;
    @Mock private BudgetPeriodEntryRepository entryRepository;
    @Mock private BudgetService budgetService;
    @Mock private CategoryRepository categoryRepository;
    @Mock private CategoryService categoryService;
    @InjectMocks private BudgetConfigService service;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @Nested
    class SetCategoryRollover {

        @Test
        void updateCategoryConfig_newConfig_creates() {
            Category cat = Category.builder().id(categoryId).workspaceId(workspaceId).build();
            when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(cat);
            when(configRepository.findByBudgetIdAndCategoryId(budgetId, categoryId))
                    .thenReturn(Optional.empty());
            when(configRepository.save(any())).thenAnswer(inv -> {
                BudgetCategoryConfig c = inv.getArgument(0);
                c.setId(UUID.randomUUID());
                return c;
            });

            UpdateBudgetCategoryConfigDto dto = UpdateBudgetCategoryConfigDto.builder()
                    .rolloverType(RolloverType.SAME_CATEGORY).build();

            BudgetCategoryConfig result = service.updateConfig(workspaceId, budgetId, categoryId, dto);
            assertThat(result.getRolloverType()).isEqualTo(RolloverType.SAME_CATEGORY);
            assertThat(result.getCategoryId()).isEqualTo(categoryId);
        }

        @Test
        void updateCategoryConfig_existingConfig_updates() {
            Category cat = Category.builder().id(categoryId).workspaceId(workspaceId).build();
            BudgetCategoryConfig existing = BudgetCategoryConfig.builder()
                    .id(UUID.randomUUID())
                    .workspaceId(workspaceId)
                    .budgetId(budgetId)
                    .categoryId(categoryId)
                    .rolloverType(RolloverType.NONE)
                    .build();

            when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(cat);
            when(configRepository.findByBudgetIdAndCategoryId(budgetId, categoryId))
                    .thenReturn(Optional.of(existing));
            when(configRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateBudgetCategoryConfigDto dto = UpdateBudgetCategoryConfigDto.builder()
                    .rolloverType(RolloverType.AVAILABLE_POOL).build();

            BudgetCategoryConfig result = service.updateConfig(workspaceId, budgetId, categoryId, dto);
            assertThat(result.getRolloverType()).isEqualTo(RolloverType.AVAILABLE_POOL);
        }

        @Test
        void updateCategoryConfig_invalidCategory_throws() {
            when(categoryService.getCategory(categoryId, workspaceId))
                    .thenThrow(new ResourceNotFoundException("Category not found"));

            UpdateBudgetCategoryConfigDto dto = UpdateBudgetCategoryConfigDto.builder()
                    .rolloverType(RolloverType.SAME_CATEGORY).build();

            assertThatThrownBy(() -> service.updateConfig(workspaceId, budgetId, categoryId, dto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class SetExpectedAmount {

        private final Budget budget = Budget.builder()
                .id(budgetId).workspaceId(workspaceId).name("Test").anchorDay1(1).build();

        @Test
        void setExpectedAmount_newEntry_creates() {
            Category cat = Category.builder().id(categoryId).workspaceId(workspaceId).build();
            when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(cat);
            when(categoryRepository.existsByParentId(categoryId)).thenReturn(false);
            when(budgetService.findBudget(workspaceId, budgetId)).thenReturn(budget);
            when(entryRepository.findByBudgetIdAndCategoryIdAndPeriodStart(any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(entryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SetExpectedAmountDto dto = SetExpectedAmountDto.builder()
                    .expectedAmount(new BigDecimal("500.00")).build();
            LocalDate periodStart = LocalDate.of(2026, 2, 1);

            service.setExpectedAmount(workspaceId, budgetId, categoryId, periodStart, dto);

            ArgumentCaptor<BudgetPeriodEntry> captor = ArgumentCaptor.forClass(BudgetPeriodEntry.class);
            verify(entryRepository).save(captor.capture());
            assertThat(captor.getValue().getExpectedAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
            assertThat(captor.getValue().getPeriodStart()).isEqualTo(periodStart);
        }

        @Test
        void setExpectedAmount_existingEntry_updates() {
            Category cat = Category.builder().id(categoryId).workspaceId(workspaceId).build();
            BudgetPeriodEntry existing = BudgetPeriodEntry.builder()
                    .workspaceId(workspaceId).budgetId(budgetId).categoryId(categoryId)
                    .periodStart(LocalDate.of(2026, 2, 1))
                    .expectedAmount(new BigDecimal("200.00")).build();

            when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(cat);
            when(categoryRepository.existsByParentId(categoryId)).thenReturn(false);
            when(budgetService.findBudget(workspaceId, budgetId)).thenReturn(budget);
            when(entryRepository.findByBudgetIdAndCategoryIdAndPeriodStart(any(), any(), any()))
                    .thenReturn(Optional.of(existing));
            when(entryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            SetExpectedAmountDto dto = SetExpectedAmountDto.builder()
                    .expectedAmount(new BigDecimal("750.00")).build();

            service.setExpectedAmount(workspaceId, budgetId, categoryId, LocalDate.of(2026, 2, 1), dto);

            ArgumentCaptor<BudgetPeriodEntry> captor = ArgumentCaptor.forClass(BudgetPeriodEntry.class);
            verify(entryRepository).save(captor.capture());
            assertThat(captor.getValue().getExpectedAmount()).isEqualByComparingTo(new BigDecimal("750.00"));
        }

        @Test
        void setExpectedAmount_invalidCategory_throws() {
            when(categoryService.getCategory(categoryId, workspaceId))
                    .thenThrow(new ResourceNotFoundException("Category not found"));

            SetExpectedAmountDto dto = SetExpectedAmountDto.builder()
                    .expectedAmount(new BigDecimal("100")).build();

            assertThatThrownBy(() -> service.setExpectedAmount(workspaceId, budgetId, categoryId, LocalDate.now(), dto))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void setExpectedAmount_nullAmount_clearsEntry() {
            Category cat = Category.builder().id(categoryId).workspaceId(workspaceId).build();
            when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(cat);
            when(categoryRepository.existsByParentId(categoryId)).thenReturn(false);
            when(budgetService.findBudget(workspaceId, budgetId)).thenReturn(budget);

            SetExpectedAmountDto dto = SetExpectedAmountDto.builder().expectedAmount(null).build();
            LocalDate periodStart = LocalDate.of(2026, 2, 1);

            service.setExpectedAmount(workspaceId, budgetId, categoryId, periodStart, dto);

            verify(entryRepository).deleteByBudgetIdAndCategoryIdAndPeriodStart(budgetId, categoryId, periodStart);
        }

        @Test
        void setExpectedAmount_misalignedPeriodStart_throws() {
            Category cat = Category.builder().id(categoryId).workspaceId(workspaceId).build();
            when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(cat);
            when(categoryRepository.existsByParentId(categoryId)).thenReturn(false);
            when(budgetService.findBudget(workspaceId, budgetId)).thenReturn(budget);

            SetExpectedAmountDto dto = SetExpectedAmountDto.builder()
                    .expectedAmount(new BigDecimal("100")).build();
            LocalDate badStart = LocalDate.of(2026, 2, 15);

            assertThatThrownBy(() -> service.setExpectedAmount(workspaceId, budgetId, categoryId, badStart, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("does not align");
        }

        @Test
        void setExpectedAmount_parentCategory_throws() {
            Category cat = Category.builder().id(categoryId).workspaceId(workspaceId).build();
            when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(cat);
            when(categoryRepository.existsByParentId(categoryId)).thenReturn(true);

            SetExpectedAmountDto dto = SetExpectedAmountDto.builder()
                    .expectedAmount(new BigDecimal("500")).build();

            assertThatThrownBy(() -> service.setExpectedAmount(workspaceId, budgetId, categoryId, LocalDate.of(2026, 2, 1), dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("parent category");
        }
    }

    @Nested
    class GetRolloverType {

        @Test
        void getRolloverType_configured_returnsConfigured() {
            BudgetCategoryConfig config = BudgetCategoryConfig.builder()
                    .rolloverType(RolloverType.SAME_CATEGORY).build();
            when(configRepository.findByBudgetIdAndCategoryId(budgetId, categoryId))
                    .thenReturn(Optional.of(config));

            assertThat(service.getRolloverType(budgetId, categoryId)).isEqualTo(RolloverType.SAME_CATEGORY);
        }

        @Test
        void getRolloverType_notConfigured_returnsNone() {
            when(configRepository.findByBudgetIdAndCategoryId(budgetId, categoryId))
                    .thenReturn(Optional.empty());

            assertThat(service.getRolloverType(budgetId, categoryId)).isEqualTo(RolloverType.NONE);
        }
    }
}
