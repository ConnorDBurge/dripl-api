package com.dripl.budget.controller;

import com.dripl.budget.dto.BudgetDto;
import com.dripl.budget.dto.CreateBudgetDto;
import com.dripl.budget.dto.UpdateBudgetDto;
import com.dripl.budget.entity.Budget;
import com.dripl.budget.service.BudgetService;
import com.dripl.common.annotation.WorkspaceId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<BudgetDto>> listBudgets(@WorkspaceId UUID workspaceId) {
        List<Budget> budgets = budgetService.listBudgets(workspaceId);
        return ResponseEntity.ok(Budget.toDtos(budgets));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BudgetDto> createBudget(
            @WorkspaceId UUID workspaceId,
            @Valid @RequestBody CreateBudgetDto dto) {
        Budget budget = budgetService.createBudget(workspaceId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(budget.toDto());
    }

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/{budgetId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BudgetDto> getBudget(
            @WorkspaceId UUID workspaceId,
            @PathVariable UUID budgetId) {
        Budget budget = budgetService.getBudget(workspaceId, budgetId);
        return ResponseEntity.ok(budget.toDto());
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(value = "/{budgetId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BudgetDto> updateBudget(
            @WorkspaceId UUID workspaceId,
            @PathVariable UUID budgetId,
            @Valid @RequestBody UpdateBudgetDto dto) {
        Budget budget = budgetService.updateBudget(workspaceId, budgetId, dto);
        return ResponseEntity.ok(budget.toDto());
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @DeleteMapping("/{budgetId}")
    public ResponseEntity<Void> deleteBudget(
            @WorkspaceId UUID workspaceId,
            @PathVariable UUID budgetId) {
        budgetService.deleteBudget(workspaceId, budgetId);
        return ResponseEntity.noContent().build();
    }
}
