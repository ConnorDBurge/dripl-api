package com.dripl.budget;

import com.dripl.budget.dto.BudgetCategoryConfigResponse;
import com.dripl.budget.dto.BudgetPeriodViewResponse;
import com.dripl.budget.dto.BudgetResponse;
import com.dripl.budget.dto.CreateBudgetInput;
import com.dripl.budget.dto.SetExpectedAmountInput;
import com.dripl.budget.dto.UpdateBudgetCategoryConfigInput;
import com.dripl.budget.dto.UpdateBudgetInput;
import com.dripl.budget.entity.Budget;
import com.dripl.budget.entity.BudgetCategoryConfig;
import com.dripl.budget.enums.RolloverType;
import com.dripl.budget.resolver.BudgetResolver;
import com.dripl.budget.service.BudgetConfigService;
import com.dripl.budget.service.BudgetService;
import com.dripl.budget.service.BudgetViewService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetResolverTest {

    @Mock
    private BudgetService budgetService;

    @Mock
    private BudgetConfigService budgetConfigService;

    @Mock
    private BudgetViewService budgetViewService;

    @InjectMocks
    private BudgetResolver budgetResolver;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();
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

    private Budget buildBudget(String name) {
        return Budget.builder()
                .id(budgetId)
                .workspaceId(workspaceId)
                .name(name)
                .anchorDay1(1)
                .createdAt(LocalDateTime.now())
                .createdBy("test-user")
                .updatedAt(LocalDateTime.now())
                .updatedBy("test-user")
                .build();
    }

    @Test
    void budgets_returnsList() {
        when(budgetService.listBudgets(workspaceId))
                .thenReturn(List.of(buildBudget("Monthly")));

        List<BudgetResponse> result = budgetResolver.budgets();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Monthly");
        assertThat(result.get(0).getId()).isEqualTo(budgetId);
        assertThat(result.get(0).getCreatedBy()).isEqualTo("test-user");
    }

    @Test
    void budgets_emptyList() {
        when(budgetService.listBudgets(workspaceId)).thenReturn(List.of());

        List<BudgetResponse> result = budgetResolver.budgets();

        assertThat(result).isEmpty();
    }

    @Test
    void budget_returnsById() {
        when(budgetService.getBudget(workspaceId, budgetId))
                .thenReturn(buildBudget("Monthly"));

        BudgetResponse result = budgetResolver.budget(budgetId);

        assertThat(result.getName()).isEqualTo("Monthly");
        assertThat(result.getId()).isEqualTo(budgetId);
    }

    @Test
    void createBudget_delegatesToService() {
        CreateBudgetInput input = CreateBudgetInput.builder()
                .name("Monthly")
                .anchorDay1(1)
                .build();
        when(budgetService.createBudget(eq(workspaceId), any(CreateBudgetInput.class)))
                .thenReturn(buildBudget("Monthly"));

        BudgetResponse result = budgetResolver.createBudget(input);

        assertThat(result.getName()).isEqualTo("Monthly");
    }

    @Test
    void updateBudget_delegatesToService() {
        UpdateBudgetInput input = UpdateBudgetInput.builder().name("Updated").build();
        Budget updated = buildBudget("Updated");
        when(budgetService.updateBudget(eq(workspaceId), eq(budgetId), any(UpdateBudgetInput.class)))
                .thenReturn(updated);

        BudgetResponse result = budgetResolver.updateBudget(budgetId, input);

        assertThat(result.getName()).isEqualTo("Updated");
    }

    @Test
    void deleteBudget_delegatesToService() {
        boolean result = budgetResolver.deleteBudget(budgetId);

        assertThat(result).isTrue();
        verify(budgetService).deleteBudget(workspaceId, budgetId);
    }

    @Test
    void budgetView_withOffset_delegatesToService() {
        BudgetPeriodViewResponse viewDto = BudgetPeriodViewResponse.builder()
                .periodStart(LocalDate.now().withDayOfMonth(1))
                .periodEnd(LocalDate.now().withDayOfMonth(1).plusMonths(1).minusDays(1))
                .budgetable(BigDecimal.ZERO)
                .totalBudgeted(BigDecimal.ZERO)
                .leftToBudget(BigDecimal.ZERO)
                .netTotalAvailable(BigDecimal.ZERO)
                .recurringExpected(BigDecimal.ZERO)
                .availablePool(BigDecimal.ZERO)
                .totalRolledOver(BigDecimal.ZERO)
                .build();
        when(budgetViewService.getView(workspaceId, budgetId, 0)).thenReturn(viewDto);

        BudgetPeriodViewResponse result = budgetResolver.budgetView(budgetId, null, 0);

        assertThat(result.getPeriodStart()).isNotNull();
        verify(budgetViewService).getView(workspaceId, budgetId, 0);
    }

    @Test
    void budgetView_withDate_delegatesToService() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        BudgetPeriodViewResponse viewDto = BudgetPeriodViewResponse.builder()
                .periodStart(date)
                .periodEnd(date.plusMonths(1).minusDays(1))
                .budgetable(BigDecimal.ZERO)
                .totalBudgeted(BigDecimal.ZERO)
                .leftToBudget(BigDecimal.ZERO)
                .netTotalAvailable(BigDecimal.ZERO)
                .recurringExpected(BigDecimal.ZERO)
                .availablePool(BigDecimal.ZERO)
                .totalRolledOver(BigDecimal.ZERO)
                .build();
        when(budgetViewService.getView(workspaceId, budgetId, date)).thenReturn(viewDto);

        BudgetPeriodViewResponse result = budgetResolver.budgetView(budgetId, "2026-01-01", null);

        assertThat(result.getPeriodStart()).isEqualTo(date);
        verify(budgetViewService).getView(workspaceId, budgetId, date);
    }

    @Test
    void updateBudgetCategoryConfig_delegatesToService() {
        UpdateBudgetCategoryConfigInput input = UpdateBudgetCategoryConfigInput.builder()
                .rolloverType(RolloverType.SAME_CATEGORY).build();
        BudgetCategoryConfig config = BudgetCategoryConfig.builder()
                .id(UUID.randomUUID())
                .categoryId(categoryId)
                .rolloverType(RolloverType.SAME_CATEGORY)
                .build();
        when(budgetConfigService.updateConfig(workspaceId, budgetId, categoryId, input)).thenReturn(config);

        BudgetCategoryConfigResponse result = budgetResolver.updateBudgetCategoryConfig(budgetId, categoryId, input);

        assertThat(result.getRolloverType()).isEqualTo(RolloverType.SAME_CATEGORY);
        assertThat(result.getCategoryId()).isEqualTo(categoryId);
    }

    @Test
    void setBudgetExpectedAmount_delegatesToService() {
        SetExpectedAmountInput input = SetExpectedAmountInput.builder()
                .expectedAmount(new BigDecimal("500")).build();
        String periodStart = "2026-02-01";

        boolean result = budgetResolver.setBudgetExpectedAmount(budgetId, categoryId, periodStart, input);

        assertThat(result).isTrue();
        verify(budgetConfigService).setExpectedAmount(
                workspaceId, budgetId, categoryId, LocalDate.of(2026, 2, 1), input);
    }
}
