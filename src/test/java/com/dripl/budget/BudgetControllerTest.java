package com.dripl.budget;

import com.dripl.budget.controller.BudgetController;
import com.dripl.budget.dto.BudgetDto;
import com.dripl.budget.dto.CreateBudgetDto;
import com.dripl.budget.dto.UpdateBudgetDto;
import com.dripl.budget.entity.Budget;
import com.dripl.budget.service.BudgetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetControllerTest {

    @Mock private BudgetService budgetService;
    @InjectMocks private BudgetController controller;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();

    @Test
    void createBudget_returns201() {
        CreateBudgetDto dto = CreateBudgetDto.builder().name("Monthly").anchorDay1(1).build();
        Budget budget = Budget.builder().id(budgetId).name("Monthly").anchorDay1(1).build();
        when(budgetService.createBudget(workspaceId, dto)).thenReturn(budget);

        ResponseEntity<BudgetDto> result = controller.createBudget(workspaceId, dto);

        assertThat(result.getStatusCode().value()).isEqualTo(201);
        assertThat(result.getBody().getName()).isEqualTo("Monthly");
        assertThat(result.getBody().getCurrentPeriodStart()).isNotNull();
    }

    @Test
    void listBudgets_returns200() {
        Budget b = Budget.builder().id(budgetId).name("A").anchorDay1(1).build();
        when(budgetService.listBudgets(workspaceId)).thenReturn(List.of(b));

        ResponseEntity<List<BudgetDto>> result = controller.listBudgets(workspaceId);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody()).hasSize(1);
    }

    @Test
    void getBudget_returns200() {
        Budget budget = Budget.builder().id(budgetId).name("Test").anchorDay1(1).build();
        when(budgetService.getBudget(workspaceId, budgetId)).thenReturn(budget);

        ResponseEntity<BudgetDto> result = controller.getBudget(workspaceId, budgetId);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void updateBudget_returns200() {
        UpdateBudgetDto dto = UpdateBudgetDto.builder().name("Updated").build();
        Budget budget = Budget.builder().id(budgetId).name("Updated").anchorDay1(1).build();
        when(budgetService.updateBudget(workspaceId, budgetId, dto)).thenReturn(budget);

        ResponseEntity<BudgetDto> result = controller.updateBudget(workspaceId, budgetId, dto);

        assertThat(result.getStatusCode().value()).isEqualTo(200);
        assertThat(result.getBody().getName()).isEqualTo("Updated");
    }

    @Test
    void deleteBudget_returns204() {
        ResponseEntity<Void> result = controller.deleteBudget(workspaceId, budgetId);

        verify(budgetService).deleteBudget(workspaceId, budgetId);
        assertThat(result.getStatusCode().value()).isEqualTo(204);
    }
}
