package com.dripl.transaction.split;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.account.service.AccountService;
import com.dripl.category.entity.Category;
import com.dripl.category.service.CategoryService;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.merchant.entity.Merchant;
import com.dripl.merchant.service.MerchantService;
import com.dripl.common.event.DomainEventPublisher;
import com.dripl.tag.entity.Tag;
import com.dripl.tag.service.TagService;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.enums.TransactionSource;
import com.dripl.transaction.enums.TransactionStatus;
import com.dripl.transaction.repository.TransactionRepository;
import com.dripl.transaction.split.dto.*;
import com.dripl.transaction.split.entity.TransactionSplit;
import com.dripl.transaction.split.repository.TransactionSplitRepository;
import com.dripl.transaction.split.service.TransactionSplitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionSplitServiceTest {

    @Mock private TransactionSplitRepository transactionSplitRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountService accountService;
    @Mock private MerchantService merchantService;
    @Mock private CategoryService categoryService;
    @Mock private TagService tagService;
    @Mock private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private TransactionSplitService transactionSplitService;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID splitId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID tagId = UUID.randomUUID();
    private final UUID txnId = UUID.randomUUID();
    private final UUID child1Id = UUID.randomUUID();
    private final UUID child2Id = UUID.randomUUID();
    private final LocalDateTime date = LocalDateTime.of(2025, 7, 14, 0, 0);

    private Transaction buildSourceTransaction() {
        return Transaction.builder()
                .id(txnId)
                .workspaceId(workspaceId)
                .accountId(accountId)
                .merchantId(merchantId)
                .date(date)
                .amount(new BigDecimal("100.00"))
                .currencyCode(CurrencyCode.USD)
                .status(TransactionStatus.POSTED)
                .source(TransactionSource.MANUAL)
                .postedAt(date)
                .build();
    }

    private TransactionSplit buildSplit() {
        return TransactionSplit.builder()
                .id(splitId)
                .workspaceId(workspaceId)
                .accountId(accountId)
                .totalAmount(new BigDecimal("100.00"))
                .currencyCode(CurrencyCode.USD)
                .date(date)
                .build();
    }

    private Transaction buildChild(UUID id, BigDecimal amount) {
        return Transaction.builder()
                .id(id)
                .workspaceId(workspaceId)
                .accountId(accountId)
                .merchantId(merchantId)
                .splitId(splitId)
                .date(date)
                .amount(amount)
                .currencyCode(CurrencyCode.USD)
                .status(TransactionStatus.POSTED)
                .source(TransactionSource.MANUAL)
                .build();
    }

    // --- listAllByWorkspaceId ---

    @Test
    void listAllByWorkspaceId_returnsList() {
        when(transactionSplitRepository.findAllByWorkspaceId(workspaceId))
                .thenReturn(List.of(buildSplit()));

        List<TransactionSplit> result = transactionSplitService.listAllByWorkspaceId(workspaceId);

        assertThat(result).hasSize(1);
    }

    // --- getTransactionSplit ---

    @Test
    void getTransactionSplit_found() {
        when(transactionSplitRepository.findByIdAndWorkspaceId(splitId, workspaceId))
                .thenReturn(Optional.of(buildSplit()));

        TransactionSplit result = transactionSplitService.getTransactionSplit(splitId, workspaceId);

        assertThat(result.getTotalAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void getTransactionSplit_notFound_throws() {
        when(transactionSplitRepository.findByIdAndWorkspaceId(splitId, workspaceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionSplitService.getTransactionSplit(splitId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- createTransactionSplit ---

    @Test
    void createTransactionSplit_success() {
        CreateTransactionSplitDto dto = CreateTransactionSplitDto.builder()
                .transactionId(txnId)
                .children(List.of(
                        SplitChildDto.builder().amount(new BigDecimal("60.00")).build(),
                        SplitChildDto.builder().amount(new BigDecimal("40.00")).build()))
                .build();

        Transaction source = buildSourceTransaction();
        when(transactionRepository.findByIdAndWorkspaceId(txnId, workspaceId))
                .thenReturn(Optional.of(source));
        when(transactionSplitRepository.save(any(TransactionSplit.class)))
                .thenAnswer(inv -> {
                    TransactionSplit s = inv.getArgument(0);
                    s.setId(splitId);
                    return s;
                });
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransactionSplit result = transactionSplitService.createTransactionSplit(workspaceId, dto);

        assertThat(result.getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getAccountId()).isEqualTo(accountId);
        assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.USD);
        assertThat(result.getDate()).isEqualTo(date);
        verify(transactionRepository).delete(source);
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void createTransactionSplit_withCategoryAndMerchant() {
        UUID newMerchantId = UUID.randomUUID();
        CreateTransactionSplitDto dto = CreateTransactionSplitDto.builder()
                .transactionId(txnId)
                .children(List.of(
                        SplitChildDto.builder()
                                .amount(new BigDecimal("60.00"))
                                .categoryId(categoryId)
                                .merchantName("Target")
                                .tagIds(Set.of(tagId))
                                .notes("Groceries")
                                .build(),
                        SplitChildDto.builder().amount(new BigDecimal("40.00")).build()))
                .build();

        when(transactionRepository.findByIdAndWorkspaceId(txnId, workspaceId))
                .thenReturn(Optional.of(buildSourceTransaction()));
        when(transactionSplitRepository.save(any(TransactionSplit.class)))
                .thenAnswer(inv -> { TransactionSplit s = inv.getArgument(0); s.setId(splitId); return s; });
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(merchantService.resolveMerchant("Target", workspaceId))
                .thenReturn(Merchant.builder().id(newMerchantId).build());
        when(categoryService.getCategory(categoryId, workspaceId))
                .thenReturn(Category.builder().id(categoryId).build());
        when(tagService.getTag(tagId, workspaceId))
                .thenReturn(Tag.builder().id(tagId).build());

        TransactionSplit result = transactionSplitService.createTransactionSplit(workspaceId, dto);

        assertThat(result.getTotalAmount()).isEqualByComparingTo("100.00");
        verify(merchantService).resolveMerchant("Target", workspaceId);
        verify(categoryService).getCategory(categoryId, workspaceId);
        verify(tagService).getTag(tagId, workspaceId);
    }

    @Test
    void createTransactionSplit_sourceNotFound_throws() {
        CreateTransactionSplitDto dto = CreateTransactionSplitDto.builder()
                .transactionId(txnId)
                .children(List.of(
                        SplitChildDto.builder().amount(new BigDecimal("50.00")).build(),
                        SplitChildDto.builder().amount(new BigDecimal("50.00")).build()))
                .build();

        when(transactionRepository.findByIdAndWorkspaceId(txnId, workspaceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionSplitService.createTransactionSplit(workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Source transaction not found");
    }

    @Test
    void createTransactionSplit_sourceInGroup_throws() {
        Transaction source = buildSourceTransaction();
        source.setGroupId(UUID.randomUUID());

        CreateTransactionSplitDto dto = CreateTransactionSplitDto.builder()
                .transactionId(txnId)
                .children(List.of(
                        SplitChildDto.builder().amount(new BigDecimal("50.00")).build(),
                        SplitChildDto.builder().amount(new BigDecimal("50.00")).build()))
                .build();

        when(transactionRepository.findByIdAndWorkspaceId(txnId, workspaceId))
                .thenReturn(Optional.of(source));

        assertThatThrownBy(() -> transactionSplitService.createTransactionSplit(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("group");
    }

    @Test
    void createTransactionSplit_sourceAlreadySplit_throws() {
        Transaction source = buildSourceTransaction();
        source.setSplitId(UUID.randomUUID());

        CreateTransactionSplitDto dto = CreateTransactionSplitDto.builder()
                .transactionId(txnId)
                .children(List.of(
                        SplitChildDto.builder().amount(new BigDecimal("50.00")).build(),
                        SplitChildDto.builder().amount(new BigDecimal("50.00")).build()))
                .build();

        when(transactionRepository.findByIdAndWorkspaceId(txnId, workspaceId))
                .thenReturn(Optional.of(source));

        assertThatThrownBy(() -> transactionSplitService.createTransactionSplit(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already part of a split");
    }

    @Test
    void createTransactionSplit_amountMismatch_throws() {
        CreateTransactionSplitDto dto = CreateTransactionSplitDto.builder()
                .transactionId(txnId)
                .children(List.of(
                        SplitChildDto.builder().amount(new BigDecimal("60.00")).build(),
                        SplitChildDto.builder().amount(new BigDecimal("30.00")).build()))
                .build();

        when(transactionRepository.findByIdAndWorkspaceId(txnId, workspaceId))
                .thenReturn(Optional.of(buildSourceTransaction()));

        assertThatThrownBy(() -> transactionSplitService.createTransactionSplit(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("90.00")
                .hasMessageContaining("100.00");
    }

    @Test
    void createTransactionSplit_mixedSignChildren_throws() {
        Transaction source = buildSourceTransaction(); // amount = 100.00

        CreateTransactionSplitDto dto = CreateTransactionSplitDto.builder()
                .transactionId(txnId)
                .children(List.of(
                        SplitChildDto.builder().amount(new BigDecimal("150.00")).build(),
                        SplitChildDto.builder().amount(new BigDecimal("-50.00")).build()))
                .build();

        when(transactionRepository.findByIdAndWorkspaceId(txnId, workspaceId))
                .thenReturn(Optional.of(source));

        assertThatThrownBy(() -> transactionSplitService.createTransactionSplit(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("positive")
                .hasMessageContaining("-50.00");
    }

    @Test
    void updateTransactionSplit_mixedSignChildren_throws() {
        UpdateTransactionSplitDto dto = UpdateTransactionSplitDto.builder()
                .children(List.of(
                        UpdateSplitChildDto.builder().id(child1Id).amount(new BigDecimal("150.00")).build(),
                        UpdateSplitChildDto.builder().id(child2Id).amount(new BigDecimal("-50.00")).build()))
                .build();

        when(transactionSplitRepository.findByIdAndWorkspaceId(splitId, workspaceId))
                .thenReturn(Optional.of(buildSplit()));

        assertThatThrownBy(() -> transactionSplitService.updateTransactionSplit(splitId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("positive")
                .hasMessageContaining("-50.00");
    }

    // --- updateTransactionSplit ---

    @Test
    void updateTransactionSplit_updateExistingChildren() {
        Transaction child1 = buildChild(child1Id, new BigDecimal("60.00"));
        Transaction child2 = buildChild(child2Id, new BigDecimal("40.00"));

        UpdateTransactionSplitDto dto = UpdateTransactionSplitDto.builder()
                .children(List.of(
                        UpdateSplitChildDto.builder().id(child1Id).amount(new BigDecimal("70.00")).build(),
                        UpdateSplitChildDto.builder().id(child2Id).amount(new BigDecimal("30.00")).build()))
                .build();

        when(transactionSplitRepository.findByIdAndWorkspaceId(splitId, workspaceId))
                .thenReturn(Optional.of(buildSplit()));
        when(transactionRepository.findAllBySplitIdAndWorkspaceId(splitId, workspaceId))
                .thenReturn(List.of(child1, child2));
        when(transactionRepository.findByIdAndWorkspaceId(child1Id, workspaceId))
                .thenReturn(Optional.of(child1));
        when(transactionRepository.findByIdAndWorkspaceId(child2Id, workspaceId))
                .thenReturn(Optional.of(child2));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        transactionSplitService.updateTransactionSplit(splitId, workspaceId, dto);

        assertThat(child1.getAmount()).isEqualByComparingTo("70.00");
        assertThat(child2.getAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void updateTransactionSplit_addNewChild_removeExisting() {
        Transaction child1 = buildChild(child1Id, new BigDecimal("60.00"));
        Transaction child2 = buildChild(child2Id, new BigDecimal("40.00"));

        UUID newMerchantId = UUID.randomUUID();
        UpdateTransactionSplitDto dto = UpdateTransactionSplitDto.builder()
                .children(List.of(
                        UpdateSplitChildDto.builder().id(child1Id).amount(new BigDecimal("50.00")).build(),
                        UpdateSplitChildDto.builder().amount(new BigDecimal("50.00")).merchantName("Walmart").build()))
                .build();

        when(transactionSplitRepository.findByIdAndWorkspaceId(splitId, workspaceId))
                .thenReturn(Optional.of(buildSplit()));
        when(transactionRepository.findAllBySplitIdAndWorkspaceId(splitId, workspaceId))
                .thenReturn(List.of(child1, child2));
        when(transactionRepository.findByIdAndWorkspaceId(child1Id, workspaceId))
                .thenReturn(Optional.of(child1));
        when(transactionRepository.findByIdAndWorkspaceId(child2Id, workspaceId))
                .thenReturn(Optional.of(child2));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(merchantService.resolveMerchant("Walmart", workspaceId))
                .thenReturn(Merchant.builder().id(newMerchantId).build());

        transactionSplitService.updateTransactionSplit(splitId, workspaceId, dto);

        verify(transactionRepository).delete(child2);
        // 2 saves: update child1 + create new child
        verify(transactionRepository, times(2)).save(any(Transaction.class));
    }

    @Test
    void updateTransactionSplit_amountMismatch_throws() {
        Transaction child1 = buildChild(child1Id, new BigDecimal("60.00"));
        Transaction child2 = buildChild(child2Id, new BigDecimal("40.00"));

        UpdateTransactionSplitDto dto = UpdateTransactionSplitDto.builder()
                .children(List.of(
                        UpdateSplitChildDto.builder().id(child1Id).amount(new BigDecimal("70.00")).build(),
                        UpdateSplitChildDto.builder().id(child2Id).amount(new BigDecimal("40.00")).build()))
                .build();

        when(transactionSplitRepository.findByIdAndWorkspaceId(splitId, workspaceId))
                .thenReturn(Optional.of(buildSplit()));

        assertThatThrownBy(() -> transactionSplitService.updateTransactionSplit(splitId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("110.00")
                .hasMessageContaining("100.00");
    }

    @Test
    void updateTransactionSplit_childNotInSplit_throws() {
        Transaction child1 = buildChild(child1Id, new BigDecimal("60.00"));
        UUID foreignId = UUID.randomUUID();

        UpdateTransactionSplitDto dto = UpdateTransactionSplitDto.builder()
                .children(List.of(
                        UpdateSplitChildDto.builder().id(child1Id).amount(new BigDecimal("50.00")).build(),
                        UpdateSplitChildDto.builder().id(foreignId).amount(new BigDecimal("50.00")).build()))
                .build();

        when(transactionSplitRepository.findByIdAndWorkspaceId(splitId, workspaceId))
                .thenReturn(Optional.of(buildSplit()));
        when(transactionRepository.findAllBySplitIdAndWorkspaceId(splitId, workspaceId))
                .thenReturn(List.of(child1));
        when(transactionRepository.findByIdAndWorkspaceId(child1Id, workspaceId))
                .thenReturn(Optional.of(child1));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> transactionSplitService.updateTransactionSplit(splitId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not part of this split");
    }

    // --- deleteTransactionSplit ---

    @Test
    void deleteTransactionSplit_dissolves() {
        when(transactionSplitRepository.findByIdAndWorkspaceId(splitId, workspaceId))
                .thenReturn(Optional.of(buildSplit()));
        when(transactionRepository.clearSplitId(splitId)).thenReturn(2);

        transactionSplitService.deleteTransactionSplit(splitId, workspaceId);

        verify(transactionRepository).clearSplitId(splitId);
        verify(transactionSplitRepository).delete(any(TransactionSplit.class));
    }

    // --- Mutual exclusivity: reject adding split transaction to group ---

    @Test
    void createTransactionSplit_newChildMerchantRequiredForNewChildren() {
        Transaction child1 = buildChild(child1Id, new BigDecimal("60.00"));
        Transaction child2 = buildChild(child2Id, new BigDecimal("40.00"));

        UpdateTransactionSplitDto dto = UpdateTransactionSplitDto.builder()
                .children(List.of(
                        UpdateSplitChildDto.builder().id(child1Id).amount(new BigDecimal("50.00")).build(),
                        UpdateSplitChildDto.builder().amount(new BigDecimal("50.00")).build())) // no merchantName
                .build();

        when(transactionSplitRepository.findByIdAndWorkspaceId(splitId, workspaceId))
                .thenReturn(Optional.of(buildSplit()));
        when(transactionRepository.findAllBySplitIdAndWorkspaceId(splitId, workspaceId))
                .thenReturn(List.of(child1, child2));
        when(transactionRepository.findByIdAndWorkspaceId(child1Id, workspaceId))
                .thenReturn(Optional.of(child1));
        when(transactionRepository.findByIdAndWorkspaceId(child2Id, workspaceId))
                .thenReturn(Optional.of(child2));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> transactionSplitService.updateTransactionSplit(splitId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Merchant name must be provided");
    }

    // --- Category Polarity Validation ---

    @Test
    void createTransactionSplit_childCategoryPolarityMismatch_throws() {
        Transaction source = buildSourceTransaction();
        source.setAmount(new BigDecimal("-100.00"));

        CreateTransactionSplitDto dto = CreateTransactionSplitDto.builder()
                .transactionId(txnId)
                .children(List.of(
                        SplitChildDto.builder().amount(new BigDecimal("-60.00")).merchantName("Target").categoryId(categoryId).build(),
                        SplitChildDto.builder().amount(new BigDecimal("-40.00")).merchantName("Target").build()))
                .build();

        when(transactionRepository.findByIdAndWorkspaceId(txnId, workspaceId)).thenReturn(Optional.of(source));
        when(transactionSplitRepository.save(any())).thenAnswer(inv -> {
            TransactionSplit s = inv.getArgument(0);
            s.setId(splitId);
            return s;
        });
        when(merchantService.resolveMerchant("Target", workspaceId)).thenReturn(
                Merchant.builder().id(merchantId).workspaceId(workspaceId).name("Target").build());
        when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(
                Category.builder().id(categoryId).workspaceId(workspaceId).name("Salary").income(true).build());
        doThrow(new BadRequestException("Negative amounts must use an expense category"))
                .when(categoryService).validateCategoryPolarity(categoryId, new BigDecimal("-60.00"), workspaceId);

        assertThatThrownBy(() -> transactionSplitService.createTransactionSplit(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expense category");
    }

    @Test
    void updateTransactionSplit_existingChildCategoryPolarityMismatch_throws() {
        TransactionSplit split = buildSplit();
        UUID newCatId = UUID.randomUUID();

        Transaction child1 = Transaction.builder().id(child1Id).workspaceId(workspaceId)
                .splitId(splitId).amount(new BigDecimal("60.00")).merchantId(merchantId).build();
        Transaction child2 = Transaction.builder().id(child2Id).workspaceId(workspaceId)
                .splitId(splitId).amount(new BigDecimal("40.00")).merchantId(merchantId).build();

        UpdateTransactionSplitDto dto = UpdateTransactionSplitDto.builder()
                .children(List.of(
                        UpdateSplitChildDto.builder().id(child1Id).amount(new BigDecimal("60.00")).categoryId(newCatId).build(),
                        UpdateSplitChildDto.builder().id(child2Id).amount(new BigDecimal("40.00")).build()))
                .build();

        when(transactionSplitRepository.findByIdAndWorkspaceId(splitId, workspaceId)).thenReturn(Optional.of(split));
        when(transactionRepository.findAllBySplitIdAndWorkspaceId(splitId, workspaceId)).thenReturn(List.of(child1, child2));
        when(transactionRepository.findByIdAndWorkspaceId(child1Id, workspaceId)).thenReturn(Optional.of(child1));
        when(categoryService.getCategory(newCatId, workspaceId)).thenReturn(
                Category.builder().id(newCatId).workspaceId(workspaceId).name("Groceries").income(false).build());
        doThrow(new BadRequestException("Positive amounts must use an income category"))
                .when(categoryService).validateCategoryPolarity(newCatId, new BigDecimal("60.00"), workspaceId);

        assertThatThrownBy(() -> transactionSplitService.updateTransactionSplit(splitId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("income category");
    }
}
