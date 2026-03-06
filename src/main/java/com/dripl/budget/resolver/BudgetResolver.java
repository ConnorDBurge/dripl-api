package com.dripl.budget.resolver;

import com.dripl.budget.dto.BudgetCategoryConfigResponse;
import com.dripl.budget.dto.BudgetPeriodViewResponse;
import com.dripl.budget.dto.BudgetResponse;
import com.dripl.budget.dto.CreateBudgetInput;
import com.dripl.budget.dto.SetExpectedAmountInput;
import com.dripl.budget.dto.UpdateBudgetCategoryConfigInput;
import com.dripl.budget.dto.UpdateBudgetInput;
import com.dripl.budget.entity.Budget;
import com.dripl.budget.entity.BudgetCategoryConfig;
import com.dripl.budget.service.BudgetConfigService;
import com.dripl.budget.service.BudgetService;
import com.dripl.budget.service.BudgetViewService;
import com.dripl.common.graphql.GraphQLContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class BudgetResolver {

    private final BudgetService budgetService;
    private final BudgetConfigService budgetConfigService;
    private final BudgetViewService budgetViewService;

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public List<BudgetResponse> budgets() {
        UUID workspaceId = GraphQLContext.workspaceId();
        return Budget.toResponses(budgetService.listBudgets(workspaceId));
    }

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public BudgetResponse budget(@Argument UUID budgetId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return budgetService.getBudget(workspaceId, budgetId).toResponse();
    }

    @PreAuthorize("hasAuthority('READ')")
    @QueryMapping
    public BudgetPeriodViewResponse budgetView(
            @Argument UUID budgetId, @Argument String date, @Argument Integer periodOffset) {
        UUID workspaceId = GraphQLContext.workspaceId();
        if (date != null) {
            return budgetViewService.getView(workspaceId, budgetId, LocalDate.parse(date));
        }
        return budgetViewService.getView(workspaceId, budgetId, periodOffset != null ? periodOffset : 0);
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public BudgetResponse createBudget(@Argument @Valid CreateBudgetInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return budgetService.createBudget(workspaceId, input).toResponse();
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public BudgetResponse updateBudget(@Argument UUID budgetId, @Argument @Valid UpdateBudgetInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        return budgetService.updateBudget(workspaceId, budgetId, input).toResponse();
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @MutationMapping
    public boolean deleteBudget(@Argument UUID budgetId) {
        UUID workspaceId = GraphQLContext.workspaceId();
        budgetService.deleteBudget(workspaceId, budgetId);
        return true;
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public BudgetCategoryConfigResponse updateBudgetCategoryConfig(
            @Argument UUID budgetId, @Argument UUID categoryId,
            @Argument @Valid UpdateBudgetCategoryConfigInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        BudgetCategoryConfig config = budgetConfigService.updateConfig(workspaceId, budgetId, categoryId, input);
        return config.toResponse();
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @MutationMapping
    public boolean setBudgetExpectedAmount(
            @Argument UUID budgetId, @Argument UUID categoryId,
            @Argument String periodStart, @Argument @Valid SetExpectedAmountInput input) {
        UUID workspaceId = GraphQLContext.workspaceId();
        budgetConfigService.setExpectedAmount(workspaceId, budgetId, categoryId, LocalDate.parse(periodStart), input);
        return true;
    }
}
