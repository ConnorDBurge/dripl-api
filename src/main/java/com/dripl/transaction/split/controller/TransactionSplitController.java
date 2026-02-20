package com.dripl.transaction.split.controller;

import com.dripl.common.annotation.WorkspaceId;
import com.dripl.transaction.split.dto.CreateTransactionSplitDto;
import com.dripl.transaction.split.dto.TransactionSplitDto;
import com.dripl.transaction.split.dto.UpdateTransactionSplitDto;
import com.dripl.transaction.split.entity.TransactionSplit;
import com.dripl.transaction.split.mapper.TransactionSplitMapper;
import com.dripl.transaction.split.service.TransactionSplitService;
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
@RequestMapping("/api/v1/transaction-splits")
public class TransactionSplitController {

    private final TransactionSplitService transactionSplitService;
    private final TransactionSplitMapper transactionSplitMapper;

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TransactionSplitDto>> listTransactionSplits(@WorkspaceId UUID workspaceId) {
        List<TransactionSplit> splits = transactionSplitService.listAllByWorkspaceId(workspaceId);
        List<TransactionSplitDto> dtos = splits.stream()
                .map(split -> transactionSplitMapper.toDto(
                        split,
                        transactionSplitService.getSplitTransactions(split.getId(), workspaceId)))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/{splitId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionSplitDto> getTransactionSplit(
            @WorkspaceId UUID workspaceId, @PathVariable UUID splitId) {
        TransactionSplit split = transactionSplitService.getTransactionSplit(splitId, workspaceId);
        return ResponseEntity.ok(transactionSplitMapper.toDto(
                split, transactionSplitService.getSplitTransactions(splitId, workspaceId)));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionSplitDto> createTransactionSplit(
            @WorkspaceId UUID workspaceId, @Valid @RequestBody CreateTransactionSplitDto dto) {
        TransactionSplit split = transactionSplitService.createTransactionSplit(workspaceId, dto);
        return ResponseEntity.status(201).body(transactionSplitMapper.toDto(
                split, transactionSplitService.getSplitTransactions(split.getId(), workspaceId)));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(value = "/{splitId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionSplitDto> updateTransactionSplit(
            @WorkspaceId UUID workspaceId, @PathVariable UUID splitId,
            @Valid @RequestBody UpdateTransactionSplitDto dto) {
        TransactionSplit split = transactionSplitService.updateTransactionSplit(splitId, workspaceId, dto);
        return ResponseEntity.ok(transactionSplitMapper.toDto(
                split, transactionSplitService.getSplitTransactions(splitId, workspaceId)));
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @DeleteMapping("/{splitId}")
    public ResponseEntity<Void> deleteTransactionSplit(
            @WorkspaceId UUID workspaceId, @PathVariable UUID splitId) {
        transactionSplitService.deleteTransactionSplit(splitId, workspaceId);
        return ResponseEntity.noContent().build();
    }
}
