package com.dripl.recurring.controller;

import com.dripl.common.annotation.WorkspaceId;
import com.dripl.recurring.dto.CreateRecurringItemDto;
import com.dripl.recurring.dto.RecurringItemDto;
import com.dripl.recurring.dto.UpdateRecurringItemDto;
import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.mapper.RecurringItemMapper;
import com.dripl.recurring.service.RecurringItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/recurring-items")
public class RecurringItemController {

    private final RecurringItemService recurringItemService;
    private final RecurringItemMapper recurringItemMapper;

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
        return ResponseEntity.status(201).body(recurringItemMapper.toDto(item));
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
}
