package com.dripl.transaction;

import com.dripl.account.entity.Account;
import com.dripl.account.enums.CurrencyCode;
import com.dripl.account.service.AccountService;
import com.dripl.category.entity.Category;
import com.dripl.category.service.CategoryService;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.common.enums.Status;
import com.dripl.merchant.entity.Merchant;
import com.dripl.merchant.repository.MerchantRepository;
import com.dripl.tag.entity.Tag;
import com.dripl.tag.service.TagService;
import com.dripl.transaction.dto.CreateTransactionDto;
import com.dripl.transaction.dto.UpdateTransactionDto;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.enums.TransactionSource;
import com.dripl.transaction.enums.TransactionStatus;
import com.dripl.transaction.mapper.TransactionMapper;
import com.dripl.transaction.repository.TransactionRepository;
import com.dripl.transaction.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountService accountService;
    @Mock
    private MerchantRepository merchantRepository;
    @Mock
    private CategoryService categoryService;
    @Mock
    private TagService tagService;

    @Spy
    private TransactionMapper transactionMapper = Mappers.getMapper(TransactionMapper.class);

    @InjectMocks
    private TransactionService transactionService;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID transactionId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID tagId = UUID.randomUUID();

    private Transaction buildTransaction() {
        return Transaction.builder()
                .id(transactionId)
                .workspaceId(workspaceId)
                .accountId(accountId)
                .merchantId(merchantId)
                .categoryId(categoryId)
                .date(LocalDateTime.of(2025, 6, 15, 0, 0))
                .amount(new BigDecimal("-42.50"))
                .currencyCode(CurrencyCode.USD)
                .status(TransactionStatus.PENDING)
                .source(TransactionSource.MANUAL)
                .pendingAt(LocalDateTime.now())
                .build();
    }

    private Account buildAccount() {
        return Account.builder().id(accountId).workspaceId(workspaceId).build();
    }

    private Merchant buildMerchant(String name) {
        return Merchant.builder().id(merchantId).workspaceId(workspaceId).name(name).status(Status.ACTIVE).build();
    }

    private Category buildCategory() {
        return Category.builder().id(categoryId).workspaceId(workspaceId).build();
    }

    private Tag buildTag() {
        return Tag.builder().id(tagId).workspaceId(workspaceId).name("Groceries").status(Status.ACTIVE).build();
    }

    // --- listAllByWorkspaceId ---

    @Test
    void listAllByWorkspaceId_returnsTransactions() {
        when(transactionRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(buildTransaction()));

        List<Transaction> result = transactionService.listAllByWorkspaceId(workspaceId);

        assertThat(result).hasSize(1);
    }

    @Test
    void listAllByWorkspaceId_emptyList() {
        when(transactionRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of());

        List<Transaction> result = transactionService.listAllByWorkspaceId(workspaceId);

        assertThat(result).isEmpty();
    }

    // --- getTransaction ---

    @Test
    void getTransaction_found() {
        Transaction txn = buildTransaction();
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        Transaction result = transactionService.getTransaction(transactionId, workspaceId);

        assertThat(result.getId()).isEqualTo(transactionId);
    }

    @Test
    void getTransaction_notFound_throws() {
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransaction(transactionId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Transaction not found");
    }

    // --- createTransaction ---

    @Test
    void createTransaction_success_existingMerchant() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .accountId(accountId)
                .merchantName("Kroger")
                .categoryId(categoryId)
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .amount(new BigDecimal("-55.00"))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, "Kroger"))
                .thenReturn(Optional.of(buildMerchant("Kroger")));
        when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(buildCategory());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.createTransaction(workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(merchantId);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("-55.00"));
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.getSource()).isEqualTo(TransactionSource.MANUAL);
        assertThat(result.getPendingAt()).isNotNull();
        assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.USD);
        verify(merchantRepository, never()).save(any());
    }

    @Test
    void createTransaction_success_autoCreatesMerchant() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .accountId(accountId)
                .merchantName("New Store")
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .amount(new BigDecimal("-10.00"))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, "New Store"))
                .thenReturn(Optional.empty());
        when(merchantRepository.save(any(Merchant.class))).thenReturn(buildMerchant("New Store"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.createTransaction(workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(merchantId);
        verify(merchantRepository).save(any(Merchant.class));
    }

    @Test
    void createTransaction_merchantLookup_caseInsensitive() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .accountId(accountId)
                .merchantName("KROGER")
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .amount(new BigDecimal("-30.00"))
                .build();

        Merchant existingMerchant = buildMerchant("Kroger");
        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, "KROGER"))
                .thenReturn(Optional.of(existingMerchant));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.createTransaction(workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(merchantId);
        verify(merchantRepository, never()).save(any());
    }

    @Test
    void createTransaction_withTags() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .accountId(accountId)
                .merchantName("Kroger")
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .amount(new BigDecimal("-20.00"))
                .tagIds(Set.of(tagId))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, "Kroger"))
                .thenReturn(Optional.of(buildMerchant("Kroger")));
        when(tagService.getTag(tagId, workspaceId)).thenReturn(buildTag());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.createTransaction(workspaceId, dto);

        assertThat(result.getTagIds()).containsExactly(tagId);
    }

    @Test
    void createTransaction_withCurrencyCode() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .accountId(accountId)
                .merchantName("Kroger")
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .amount(new BigDecimal("-20.00"))
                .currencyCode(CurrencyCode.EUR)
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, "Kroger"))
                .thenReturn(Optional.of(buildMerchant("Kroger")));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.createTransaction(workspaceId, dto);

        assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
    }

    @Test
    void createTransaction_accountNotInWorkspace_throws() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .accountId(accountId)
                .merchantName("Kroger")
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .amount(new BigDecimal("-20.00"))
                .build();

        when(accountService.getAccount(accountId, workspaceId))
                .thenThrow(new ResourceNotFoundException("Account not found"));

        assertThatThrownBy(() -> transactionService.createTransaction(workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void createTransaction_categoryNotInWorkspace_throws() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .accountId(accountId)
                .merchantName("Kroger")
                .categoryId(categoryId)
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .amount(new BigDecimal("-20.00"))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, "Kroger"))
                .thenReturn(Optional.of(buildMerchant("Kroger")));
        when(categoryService.getCategory(categoryId, workspaceId))
                .thenThrow(new ResourceNotFoundException("Category not found"));

        assertThatThrownBy(() -> transactionService.createTransaction(workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void createTransaction_tagNotInWorkspace_throws() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .accountId(accountId)
                .merchantName("Kroger")
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .amount(new BigDecimal("-20.00"))
                .tagIds(Set.of(tagId))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, "Kroger"))
                .thenReturn(Optional.of(buildMerchant("Kroger")));
        when(tagService.getTag(tagId, workspaceId))
                .thenThrow(new ResourceNotFoundException("Tag not found"));

        assertThatThrownBy(() -> transactionService.createTransaction(workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tag");
    }

    @Test
    void createTransaction_noCategoryId_success() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .accountId(accountId)
                .merchantName("Kroger")
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .amount(new BigDecimal("-20.00"))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, "Kroger"))
                .thenReturn(Optional.of(buildMerchant("Kroger")));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.createTransaction(workspaceId, dto);

        assertThat(result.getCategoryId()).isNull();
        verify(categoryService, never()).getCategory(any(), any());
    }

    // --- updateTransaction ---

    @Test
    void updateTransaction_account() {
        Transaction txn = buildTransaction();
        UUID newAccountId = UUID.randomUUID();
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(accountService.getAccount(newAccountId, workspaceId))
                .thenReturn(Account.builder().id(newAccountId).workspaceId(workspaceId).build());
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().accountId(newAccountId).build();
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getAccountId()).isEqualTo(newAccountId);
    }

    @Test
    void updateTransaction_merchantName_existingMerchant() {
        Transaction txn = buildTransaction();
        UUID newMerchantId = UUID.randomUUID();
        Merchant existingMerchant = Merchant.builder().id(newMerchantId).workspaceId(workspaceId).name("Target").build();

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(merchantRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, "Target"))
                .thenReturn(Optional.of(existingMerchant));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().merchantName("Target").build();
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(newMerchantId);
    }

    @Test
    void updateTransaction_merchantName_autoCreates() {
        Transaction txn = buildTransaction();
        UUID newMerchantId = UUID.randomUUID();
        Merchant newMerchant = Merchant.builder().id(newMerchantId).workspaceId(workspaceId).name("New Place").build();

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(merchantRepository.findByWorkspaceIdAndNameIgnoreCase(workspaceId, "New Place"))
                .thenReturn(Optional.empty());
        when(merchantRepository.save(any(Merchant.class))).thenReturn(newMerchant);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().merchantName("New Place").build();
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(newMerchantId);
    }

    @Test
    void updateTransaction_setCategoryId() {
        Transaction txn = buildTransaction();
        txn.setCategoryId(null);
        UUID newCategoryId = UUID.randomUUID();

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(categoryService.getCategory(newCategoryId, workspaceId))
                .thenReturn(Category.builder().id(newCategoryId).workspaceId(workspaceId).build());
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = new UpdateTransactionDto();
        dto.assignCategoryId(newCategoryId);
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getCategoryId()).isEqualTo(newCategoryId);
    }

    @Test
    void updateTransaction_clearCategoryId() {
        Transaction txn = buildTransaction();
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = new UpdateTransactionDto();
        dto.assignCategoryId(null);
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getCategoryId()).isNull();
    }

    @Test
    void updateTransaction_categoryIdNotSpecified_doesNotChange() {
        Transaction txn = buildTransaction();
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getCategoryId()).isEqualTo(categoryId);
    }

    @Test
    void updateTransaction_date() {
        Transaction txn = buildTransaction();
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime newDate = LocalDateTime.of(2025, 8, 1, 0, 0);
        UpdateTransactionDto dto = UpdateTransactionDto.builder().date(newDate).build();
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getDate()).isEqualTo(newDate);
    }

    @Test
    void updateTransaction_amount() {
        Transaction txn = buildTransaction();
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().amount(new BigDecimal("-99.99")).build();
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("-99.99"));
    }

    @Test
    void updateTransaction_notes_set() {
        Transaction txn = buildTransaction();
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = new UpdateTransactionDto();
        dto.assignNotes("Weekly groceries");
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getNotes()).isEqualTo("Weekly groceries");
    }

    @Test
    void updateTransaction_notes_clear() {
        Transaction txn = buildTransaction();
        txn.setNotes("Old notes");
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = new UpdateTransactionDto();
        dto.assignNotes(null);
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getNotes()).isNull();
    }

    @Test
    void updateTransaction_tags_set() {
        Transaction txn = buildTransaction();
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(tagService.getTag(tagId, workspaceId)).thenReturn(buildTag());
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = new UpdateTransactionDto();
        dto.assignTagIds(Set.of(tagId));
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getTagIds()).containsExactly(tagId);
    }

    @Test
    void updateTransaction_tags_clear() {
        Transaction txn = buildTransaction();
        txn.setTagIds(Set.of(tagId));
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = new UpdateTransactionDto();
        dto.assignTagIds(Set.of());
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getTagIds()).isEmpty();
    }

    @Test
    void updateTransaction_tagsNotSpecified_doesNotChange() {
        Transaction txn = buildTransaction();
        txn.setTagIds(Set.of(tagId));
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getTagIds()).containsExactly(tagId);
    }

    // --- status transitions ---

    @Test
    void updateTransaction_status_pendingToPosted() {
        Transaction txn = buildTransaction();
        assertThat(txn.getPostedAt()).isNull();

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().status(TransactionStatus.POSTED).build();
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.POSTED);
        assertThat(result.getPostedAt()).isNotNull();
    }

    @Test
    void updateTransaction_status_postedToPending() {
        Transaction txn = buildTransaction();
        txn.setStatus(TransactionStatus.POSTED);
        txn.setPostedAt(LocalDateTime.now().minusDays(1));
        LocalDateTime oldPendingAt = txn.getPendingAt();

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().status(TransactionStatus.PENDING).build();
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.getPendingAt()).isAfterOrEqualTo(oldPendingAt);
    }

    @Test
    void updateTransaction_status_toArchived() {
        Transaction txn = buildTransaction();
        LocalDateTime pendingAt = txn.getPendingAt();

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().status(TransactionStatus.ARCHIVED).build();
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.ARCHIVED);
        assertThat(result.getPendingAt()).isEqualTo(pendingAt);
        assertThat(result.getPostedAt()).isNull();
    }

    @Test
    void updateTransaction_accountNotInWorkspace_throws() {
        Transaction txn = buildTransaction();
        UUID badAccountId = UUID.randomUUID();
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(accountService.getAccount(badAccountId, workspaceId))
                .thenThrow(new ResourceNotFoundException("Account not found"));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().accountId(badAccountId).build();

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void updateTransaction_categoryNotInWorkspace_throws() {
        Transaction txn = buildTransaction();
        UUID badCategoryId = UUID.randomUUID();
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(categoryService.getCategory(badCategoryId, workspaceId))
                .thenThrow(new ResourceNotFoundException("Category not found"));

        UpdateTransactionDto dto = new UpdateTransactionDto();
        dto.assignCategoryId(badCategoryId);

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void updateTransaction_notFound_throws() {
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.empty());

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- deleteTransaction ---

    @Test
    void deleteTransaction_success() {
        Transaction txn = buildTransaction();
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        transactionService.deleteTransaction(transactionId, workspaceId);

        verify(transactionRepository).delete(txn);
    }

    @Test
    void deleteTransaction_notFound_throws() {
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.deleteTransaction(transactionId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transactionRepository, never()).delete(any());
    }

}
