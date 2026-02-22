package com.dripl.budget.controller;

import com.dripl.budget.dto.BudgetCategoryConfigDto;
import com.dripl.budget.dto.BudgetPeriodViewDto;
import com.dripl.budget.dto.SetExpectedAmountDto;
import com.dripl.budget.dto.UpdateBudgetCategoryConfigDto;
import com.dripl.budget.entity.BudgetCategoryConfig;
import com.dripl.budget.service.BudgetConfigService;
import com.dripl.budget.service.BudgetViewService;
import com.dripl.common.annotation.WorkspaceId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/budgets/{budgetId}")
public class BudgetConfigController {

    private final BudgetConfigService budgetConfigService;
    private final BudgetViewService budgetViewService;

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/view", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BudgetPeriodViewDto> getView(
            @WorkspaceId UUID workspaceId,
            @PathVariable UUID budgetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int periodOffset) {
        if (date != null) {
            return ResponseEntity.ok(budgetViewService.getView(workspaceId, budgetId, date));
        }
        return ResponseEntity.ok(budgetViewService.getView(workspaceId, budgetId, periodOffset));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(value = "/categories/{categoryId}/config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BudgetCategoryConfigDto> updateCategoryConfig(
            @WorkspaceId UUID workspaceId,
            @PathVariable UUID budgetId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateBudgetCategoryConfigDto dto) {
        BudgetCategoryConfig config = budgetConfigService.updateConfig(workspaceId, budgetId, categoryId, dto);
        return ResponseEntity.ok(config.toDto());
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PutMapping(value = "/categories/{categoryId}/expected", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> setExpectedAmount(
            @WorkspaceId UUID workspaceId,
            @PathVariable UUID budgetId,
            @PathVariable UUID categoryId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @Valid @RequestBody SetExpectedAmountDto dto) {
        budgetConfigService.setExpectedAmount(workspaceId, budgetId, categoryId, periodStart, dto);
        return ResponseEntity.ok().build();
    }
}
