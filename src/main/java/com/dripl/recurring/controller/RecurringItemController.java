package com.dripl.recurring.controller;

import com.dripl.common.annotation.WorkspaceId;
import com.dripl.recurring.dto.CreateRecurringItemDto;
import com.dripl.recurring.dto.RecurringItemDto;
import com.dripl.recurring.dto.RecurringItemMonthViewDto;
import com.dripl.recurring.dto.UpdateRecurringItemDto;
import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.mapper.RecurringItemMapper;
import com.dripl.recurring.service.RecurringItemService;
import com.dripl.recurring.service.RecurringItemViewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/recurring-items")
public class RecurringItemController {

    private final RecurringItemService recurringItemService;
    private final RecurringItemViewService recurringItemViewService;
    private final RecurringItemMapper recurringItemMapper;

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/view", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecurringItemMonthViewDto> getMonthView(
            @WorkspaceId UUID workspaceId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Integer periodOffset) {

        return ResponseEntity.ok(recurringItemViewService.getMonthView(workspaceId, month, periodOffset));
    }

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<RecurringItemDto>> listRecurringItems(@WorkspaceId UUID workspaceId) {
        List<RecurringItem> items = recurringItemService.listAllByWorkspaceId(workspaceId);
        return ResponseEntity.ok(recurringItemMapper.toDtos(items));
    }

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/{recurringItemId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecurringItemDto> getRecurringItem(
            @WorkspaceId UUID workspaceId, @PathVariable UUID recurringItemId) {
        RecurringItem item = recurringItemService.getRecurringItem(recurringItemId, workspaceId);
        return ResponseEntity.ok(recurringItemMapper.toDto(item));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecurringItemDto> createRecurringItem(
            @WorkspaceId UUID workspaceId, @Valid @RequestBody CreateRecurringItemDto dto) {
        RecurringItem item = recurringItemService.createRecurringItem(workspaceId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(recurringItemMapper.toDto(item));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(value = "/{recurringItemId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecurringItemDto> updateRecurringItem(
            @WorkspaceId UUID workspaceId, @PathVariable UUID recurringItemId,
            @Valid @RequestBody UpdateRecurringItemDto dto) {
        RecurringItem item = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);
        return ResponseEntity.ok(recurringItemMapper.toDto(item));
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @DeleteMapping("/{recurringItemId}")
    public ResponseEntity<Void> deleteRecurringItem(
            @WorkspaceId UUID workspaceId, @PathVariable UUID recurringItemId) {
        recurringItemService.deleteRecurringItem(recurringItemId, workspaceId);
        return ResponseEntity.noContent().build();
    }

    // TODO: Implement per-period amount overrides for recurring items.
    //  - Create RecurringItemPeriodEntry entity + repository + migration
    //  - Create SetRecurringItemExpectedDto (nullable BigDecimal expectedAmount; null = clear)
    //  - Implement service methods in RecurringItemService
    //  - Update BudgetViewService.recurringItemsExpected to check for period overrides
    //  - Add unit tests, controller tests, and integration tests

    @PreAuthorize("hasAuthority('WRITE')")
    @PutMapping(value = "/{recurringItemId}/expected", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> setExpectedAmount(
            @WorkspaceId UUID workspaceId,
            @PathVariable UUID recurringItemId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
