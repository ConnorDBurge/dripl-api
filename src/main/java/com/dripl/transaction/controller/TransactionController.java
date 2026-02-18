package com.dripl.transaction.controller;

import com.dripl.common.annotation.WorkspaceId;
import com.dripl.transaction.dto.CreateTransactionDto;
import com.dripl.transaction.dto.TransactionDto;
import com.dripl.transaction.dto.UpdateTransactionDto;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.mapper.TransactionMapper;
import com.dripl.transaction.service.TransactionService;
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
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<TransactionDto>> listTransactions(@WorkspaceId UUID workspaceId) {
        List<Transaction> transactions = transactionService.listAllByWorkspaceId(workspaceId);
        return ResponseEntity.ok(transactionMapper.toDtos(transactions));
    }

    @PreAuthorize("hasAuthority('READ')")
    @GetMapping(value = "/{transactionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionDto> getTransaction(
            @WorkspaceId UUID workspaceId, @PathVariable UUID transactionId) {
        Transaction transaction = transactionService.getTransaction(transactionId, workspaceId);
        return ResponseEntity.ok(transactionMapper.toDto(transaction));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionDto> createTransaction(
            @WorkspaceId UUID workspaceId, @Valid @RequestBody CreateTransactionDto dto) {
        Transaction transaction = transactionService.createTransaction(workspaceId, dto);
        return ResponseEntity.status(201).body(transactionMapper.toDto(transaction));
    }

    @PreAuthorize("hasAuthority('WRITE')")
    @PatchMapping(value = "/{transactionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TransactionDto> updateTransaction(
            @WorkspaceId UUID workspaceId, @PathVariable UUID transactionId,
            @Valid @RequestBody UpdateTransactionDto dto) {
        Transaction transaction = transactionService.updateTransaction(transactionId, workspaceId, dto);
        return ResponseEntity.ok(transactionMapper.toDto(transaction));
    }

    @PreAuthorize("hasAuthority('DELETE')")
    @DeleteMapping("/{transactionId}")
    public ResponseEntity<Void> deleteTransaction(
            @WorkspaceId UUID workspaceId, @PathVariable UUID transactionId) {
        transactionService.deleteTransaction(transactionId, workspaceId);
        return ResponseEntity.noContent().build();
    }
}
