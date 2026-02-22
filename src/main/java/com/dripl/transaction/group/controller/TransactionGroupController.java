package com.dripl.transaction.group.controller;

import com.dripl.common.annotation.WorkspaceId;
import com.dripl.transaction.group.dto.CreateTransactionGroupDto;
import com.dripl.transaction.group.dto.TransactionGroupDto;
import com.dripl.transaction.group.dto.UpdateTransactionGroupDto;
import com.dripl.transaction.group.entity.TransactionGroup;
import com.dripl.transaction.group.mapper.TransactionGroupMapper;
import com.dripl.transaction.group.service.TransactionGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/transaction-groups")
public class TransactionGroupController {

    private final TransactionGroupService transactionGroupService;
    private final TransactionGroupMapper transactionGroupMapper;

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TransactionGroupDto>> listTransactionGroups(@WorkspaceId UUID workspaceId) {
        List<TransactionGroup> groups = transactionGroupService.listAllByWorkspaceId(workspaceId);
        List<TransactionGroupDto> dtos = groups.stream()
                .map(group -> transactionGroupMapper.toDto(
                        group,
                        transactionGroupService.getGroupTransactions(group.getId(), workspaceId)))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/{groupId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionGroupDto> getTransactionGroup(
            @WorkspaceId UUID workspaceId, @PathVariable UUID groupId) {
        TransactionGroup group = transactionGroupService.getTransactionGroup(groupId, workspaceId);
        return ResponseEntity.ok(transactionGroupMapper.toDto(
                group, transactionGroupService.getGroupTransactions(groupId, workspaceId)));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionGroupDto> createTransactionGroup(
            @WorkspaceId UUID workspaceId, @Valid @RequestBody CreateTransactionGroupDto dto) {
        TransactionGroup group = transactionGroupService.createTransactionGroup(workspaceId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionGroupMapper.toDto(
                group, transactionGroupService.getGroupTransactions(group.getId(), workspaceId)));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(value = "/{groupId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionGroupDto> updateTransactionGroup(
            @WorkspaceId UUID workspaceId, @PathVariable UUID groupId,
            @Valid @RequestBody UpdateTransactionGroupDto dto) {
        TransactionGroup group = transactionGroupService.updateTransactionGroup(groupId, workspaceId, dto);
        return ResponseEntity.ok(transactionGroupMapper.toDto(
                group, transactionGroupService.getGroupTransactions(groupId, workspaceId)));
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteTransactionGroup(
            @WorkspaceId UUID workspaceId, @PathVariable UUID groupId) {
        transactionGroupService.deleteTransactionGroup(groupId, workspaceId);
        return ResponseEntity.noContent().build();
    }
}
