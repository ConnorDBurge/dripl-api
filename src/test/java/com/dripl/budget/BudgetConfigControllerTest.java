package com.dripl.budget;

import com.dripl.budget.controller.BudgetConfigController;
import com.dripl.budget.dto.BudgetCategoryConfigDto;
import com.dripl.budget.dto.BudgetPeriodViewDto;
import com.dripl.budget.dto.SetExpectedAmountDto;
import com.dripl.budget.dto.UpdateBudgetCategoryConfigDto;
import com.dripl.budget.entity.BudgetCategoryConfig;
import com.dripl.budget.enums.RolloverType;
import com.dripl.budget.service.BudgetConfigService;
import com.dripl.budget.service.BudgetViewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetConfigControllerTest {

    @Mock private BudgetConfigService budgetConfigService;
    @Mock private BudgetViewService budgetViewService;
    @InjectMocks private BudgetConfigController controller;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();

    @Test
    void getView_withOffset_returnsOk() {
        BudgetPeriodViewDto dto = BudgetPeriodViewDto.builder().periodStart(LocalDate.now()).build();
        when(budgetViewService.getView(workspaceId, budgetId, 0)).thenReturn(dto);

        ResponseEntity<BudgetPeriodViewDto> response = controller.getView(workspaceId, budgetId, null, 0);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void getView_withNegativeOffset_returnsOk() {
        BudgetPeriodViewDto dto = BudgetPeriodViewDto.builder().periodStart(LocalDate.now()).build();
        when(budgetViewService.getView(workspaceId, budgetId, -1)).thenReturn(dto);

        ResponseEntity<BudgetPeriodViewDto> response = controller.getView(workspaceId, budgetId, null, -1);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void getView_withPeriodStart_returnsOk() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        BudgetPeriodViewDto dto = BudgetPeriodViewDto.builder().periodStart(date).build();
        when(budgetViewService.getView(workspaceId, budgetId, date)).thenReturn(dto);

        ResponseEntity<BudgetPeriodViewDto> response = controller.getView(workspaceId, budgetId, date, 0);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(budgetViewService).getView(workspaceId, budgetId, date);
    }

    @Test
    void updateCategoryConfig_returnsOk() {
        UpdateBudgetCategoryConfigDto dto = UpdateBudgetCategoryConfigDto.builder()
                .rolloverType(RolloverType.SAME_CATEGORY).build();
        BudgetCategoryConfig config = BudgetCategoryConfig.builder()
                .id(UUID.randomUUID())
                .categoryId(categoryId).rolloverType(RolloverType.SAME_CATEGORY).build();
        when(budgetConfigService.updateConfig(workspaceId, budgetId, categoryId, dto)).thenReturn(config);

        ResponseEntity<BudgetCategoryConfigDto> response = controller.updateCategoryConfig(workspaceId, budgetId, categoryId, dto);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void setExpectedAmount_returnsOk() {
        LocalDate periodStart = LocalDate.of(2026, 2, 1);
        SetExpectedAmountDto dto = SetExpectedAmountDto.builder()
                .expectedAmount(new BigDecimal("500")).build();

        ResponseEntity<Void> response = controller.setExpectedAmount(workspaceId, budgetId, categoryId, periodStart, dto);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(budgetConfigService).setExpectedAmount(workspaceId, budgetId, categoryId, periodStart, dto);
    }

    @Test
    void setExpectedAmount_nullAmount_returnsOk() {
        LocalDate periodStart = LocalDate.of(2026, 2, 1);
        SetExpectedAmountDto dto = SetExpectedAmountDto.builder().expectedAmount(null).build();

        ResponseEntity<Void> response = controller.setExpectedAmount(workspaceId, budgetId, categoryId, periodStart, dto);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(budgetConfigService).setExpectedAmount(workspaceId, budgetId, categoryId, periodStart, dto);
    }
}
