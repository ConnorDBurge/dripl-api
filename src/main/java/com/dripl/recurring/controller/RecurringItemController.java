package com.dripl.recurring.controller;

import com.dripl.common.annotation.WorkspaceId;
import com.dripl.recurring.dto.CreateRecurringItemDto;
import com.dripl.recurring.dto.RecurringItemDto;
import com.dripl.recurring.dto.RecurringItemMonthViewDto;
import com.dripl.recurring.dto.RecurringItemOverrideDto;
import com.dripl.recurring.dto.SetOccurrenceOverrideDto;
import com.dripl.recurring.dto.UpdateOccurrenceOverrideDto;
import com.dripl.recurring.dto.RecurringItemOverrideDto;
import com.dripl.recurring.dto.UpdateRecurringItemDto;
import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.entity.RecurringItemOverride;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @PreAuthorize("hasAuthority('WRITE')")
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecurringItemDto> createRecurringItem(
            @WorkspaceId UUID workspaceId, @Valid @RequestBody CreateRecurringItemDto dto) {
        RecurringItem item = recurringItemService.createRecurringItem(workspaceId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(recurringItemMapper.toDto(item));
    }

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/{recurringItemId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecurringItemDto> getRecurringItem(
            @WorkspaceId UUID workspaceId, @PathVariable UUID recurringItemId) {
        RecurringItem item = recurringItemService.getRecurringItem(recurringItemId, workspaceId);
        return ResponseEntity.ok(recurringItemMapper.toDto(item));
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

    @PreAuthorize("hasAuthority('WRITE')")
    @PostMapping(value = "/{recurringItemId}/overrides", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecurringItemOverrideDto> createOverride(
            @WorkspaceId UUID workspaceId,
            @PathVariable UUID recurringItemId,
            @Valid @RequestBody SetOccurrenceOverrideDto dto) {

        RecurringItemOverride override = recurringItemService.createOverride(recurringItemId, workspaceId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(toOverrideDto(override));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PutMapping(value = "/{recurringItemId}/overrides/{overrideId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecurringItemOverrideDto> updateOverride(
            @WorkspaceId UUID workspaceId,
            @PathVariable UUID recurringItemId,
            @PathVariable UUID overrideId,
            @Valid @RequestBody UpdateOccurrenceOverrideDto dto) {

        RecurringItemOverride override = recurringItemService.updateOverride(overrideId, workspaceId, dto);
        return ResponseEntity.ok(toOverrideDto(override));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @DeleteMapping("/{recurringItemId}/overrides/{overrideId}")
    public ResponseEntity<Void> deleteOverride(
            @WorkspaceId UUID workspaceId,
            @PathVariable UUID recurringItemId,
            @PathVariable UUID overrideId) {

        recurringItemService.deleteOverride(overrideId, workspaceId);
        return ResponseEntity.noContent().build();
    }

    private RecurringItemOverrideDto toOverrideDto(RecurringItemOverride override) {
        return RecurringItemOverrideDto.builder()
                .id(override.getId())
                .recurringItemId(override.getRecurringItemId())
                .occurrenceDate(override.getOccurrenceDate())
                .amount(override.getAmount())
                .notes(override.getNotes())
                .createdAt(override.getCreatedAt())
                .createdBy(override.getCreatedBy())
                .updatedAt(override.getUpdatedAt())
                .updatedBy(override.getUpdatedBy())
                .build();
    }
}
