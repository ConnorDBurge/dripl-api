package com.dripl.transaction;

import com.dripl.account.entity.Account;
import com.dripl.account.enums.CurrencyCode;
import com.dripl.account.service.AccountService;
import com.dripl.category.entity.Category;
import com.dripl.category.service.CategoryService;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.common.enums.Status;
import com.dripl.common.event.DomainEventPublisher;
import com.dripl.merchant.entity.Merchant;
import com.dripl.merchant.service.MerchantService;
import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.enums.FrequencyGranularity;
import com.dripl.recurring.enums.RecurringItemStatus;
import com.dripl.recurring.service.RecurringItemService;
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
import com.dripl.transaction.split.entity.TransactionSplit;
import com.dripl.transaction.split.repository.TransactionSplitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
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
    private MerchantService merchantService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private TagService tagService;
    @Mock
    private RecurringItemService recurringItemService;
    @Mock
    private TransactionSplitRepository transactionSplitRepository;
    @Mock
    private DomainEventPublisher domainEventPublisher;

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
    private final UUID recurringItemId = UUID.randomUUID();

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

    private RecurringItem buildRecurringItem() {
        return RecurringItem.builder()
                .id(recurringItemId)
                .workspaceId(workspaceId)
                .accountId(accountId)
                .merchantId(merchantId)
                .categoryId(categoryId)
                .amount(new BigDecimal("-15.99"))
                .currencyCode(CurrencyCode.EUR)
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .frequencyQuantity(1)
                .anchorDates(List.of(LocalDateTime.of(2025, 1, 15, 0, 0)))
                .startDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                .status(RecurringItemStatus.ACTIVE)
                .tagIds(Set.of(tagId))
                .build();
    }

    // --- listAllByWorkspaceId ---

    @Test
    void listAllByWorkspaceId_returnsTransactions() {
        when(transactionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(buildTransaction())));

        org.springframework.data.domain.Page<Transaction> result = transactionService.listAll(
                org.springframework.data.jpa.domain.Specification.where(null),
                org.springframework.data.domain.PageRequest.of(0, 25));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void listAllByWorkspaceId_emptyList() {
        when(transactionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());

        org.springframework.data.domain.Page<Transaction> result = transactionService.listAll(
                org.springframework.data.jpa.domain.Specification.where(null),
                org.springframework.data.domain.PageRequest.of(0, 25));

        assertThat(result.getContent()).isEmpty();
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
        when(merchantService.resolveMerchant("Kroger", workspaceId)).thenReturn(buildMerchant("Kroger"));
        when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(buildCategory());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.createTransaction(workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(merchantId);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("-55.00"));
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.getSource()).isEqualTo(TransactionSource.MANUAL);
        assertThat(result.getPendingAt()).isNotNull();
        assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.USD);
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
        when(merchantService.resolveMerchant("New Store", workspaceId)).thenReturn(buildMerchant("New Store"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.createTransaction(workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(merchantId);
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
        when(merchantService.resolveMerchant("KROGER", workspaceId)).thenReturn(existingMerchant);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.createTransaction(workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(merchantId);
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
        when(merchantService.resolveMerchant("Kroger", workspaceId)).thenReturn(buildMerchant("Kroger"));
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
        when(merchantService.resolveMerchant("Kroger", workspaceId)).thenReturn(buildMerchant("Kroger"));
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
        when(merchantService.resolveMerchant("Kroger", workspaceId)).thenReturn(buildMerchant("Kroger"));
        when(categoryService.getCategory(categoryId, workspaceId))
                .thenThrow(new ResourceNotFoundException("Category not found"));

        assertThatThrownBy(() -> transactionService.createTransaction(workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void createTransaction_categoryIsGroup_throws() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .accountId(accountId)
                .merchantName("Kroger")
                .categoryId(categoryId)
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .amount(new BigDecimal("-20.00"))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantService.resolveMerchant("Kroger", workspaceId)).thenReturn(buildMerchant("Kroger"));
        when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(buildCategory());
        doThrow(new BadRequestException("Cannot assign a transaction to a parent category group"))
                .when(categoryService).validateNotGroup(categoryId);

        assertThatThrownBy(() -> transactionService.createTransaction(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("parent category group");
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
        when(merchantService.resolveMerchant("Kroger", workspaceId)).thenReturn(buildMerchant("Kroger"));
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
        when(merchantService.resolveMerchant("Kroger", workspaceId)).thenReturn(buildMerchant("Kroger"));
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
        when(merchantService.resolveMerchant("Target", workspaceId)).thenReturn(existingMerchant);
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
        when(merchantService.resolveMerchant("New Place", workspaceId)).thenReturn(newMerchant);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().merchantName("New Place").build();
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(newMerchantId);
    }

    @Test
    void updateTransaction_merchantId_setsDirectly() {
        Transaction txn = buildTransaction();
        UUID newMerchantId = UUID.randomUUID();
        Merchant merchant = Merchant.builder().id(newMerchantId).workspaceId(workspaceId).name("Direct Merchant").build();

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(merchantService.getMerchant(newMerchantId, workspaceId)).thenReturn(merchant);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignMerchantId(newMerchantId);
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(newMerchantId);
        verify(merchantService, never()).resolveMerchant(any(), any());
    }

    @Test
    void updateTransaction_merchantId_null_clearsMerchant() {
        Transaction txn = buildTransaction();

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignMerchantId(null);
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getMerchantId()).isNull();
    }

    @Test
    void updateTransaction_merchantId_takesPrecedenceOverMerchantName() {
        Transaction txn = buildTransaction();
        UUID newMerchantId = UUID.randomUUID();
        Merchant merchant = Merchant.builder().id(newMerchantId).workspaceId(workspaceId).name("Direct").build();

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(merchantService.getMerchant(newMerchantId, workspaceId)).thenReturn(merchant);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().merchantName("Ignored Name").build();
        dto.assignMerchantId(newMerchantId);
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(newMerchantId);
        verify(merchantService, never()).resolveMerchant(any(), any());
    }

    @Test
    void updateTransaction_merchantId_notSpecified_doesNotClear() {
        Transaction txn = buildTransaction();

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(merchantId);
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
    void updateTransaction_categoryIsGroup_throws() {
        Transaction txn = buildTransaction();
        UUID groupCategoryId = UUID.randomUUID();
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(categoryService.getCategory(groupCategoryId, workspaceId))
                .thenReturn(Category.builder().id(groupCategoryId).workspaceId(workspaceId).build());
        doThrow(new BadRequestException("Cannot assign a transaction to a parent category group"))
                .when(categoryService).validateNotGroup(groupCategoryId);

        UpdateTransactionDto dto = new UpdateTransactionDto();
        dto.assignCategoryId(groupCategoryId);

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("parent category group");
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
        verify(transactionRepository, never()).delete(any(Transaction.class));
    }

    // --- createTransaction with recurringItemId inheritance ---

    @Test
    void createTransaction_withRecurringItem_inheritsDefaults() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .recurringItemId(recurringItemId)
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .build();

        when(recurringItemService.getRecurringItem(recurringItemId, workspaceId)).thenReturn(buildRecurringItem());
        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(buildCategory());
        when(tagService.getTag(tagId, workspaceId)).thenReturn(buildTag());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.createTransaction(workspaceId, dto);

        assertThat(result.getAccountId()).isEqualTo(accountId);
        assertThat(result.getMerchantId()).isEqualTo(merchantId);
        assertThat(result.getCategoryId()).isEqualTo(categoryId);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("-15.99"));
        assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
        assertThat(result.getTagIds()).containsExactly(tagId);
        assertThat(result.getRecurringItemId()).isEqualTo(recurringItemId);
    }

    @Test
    void createTransaction_withRecurringItem_lockedFieldsFromRI() {
        UUID overrideAccountId = UUID.randomUUID();

        CreateTransactionDto dto = CreateTransactionDto.builder()
                .recurringItemId(recurringItemId)
                .accountId(overrideAccountId)
                .merchantName("Override Store")
                .categoryId(UUID.randomUUID())
                .amount(new BigDecimal("-99.99"))
                .currencyCode(CurrencyCode.USD)
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .tagIds(Set.of())
                .build();

        when(recurringItemService.getRecurringItem(recurringItemId, workspaceId)).thenReturn(buildRecurringItem());
        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(buildCategory());
        when(tagService.getTag(tagId, workspaceId)).thenReturn(buildTag());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.createTransaction(workspaceId, dto);

        // Locked fields come from RI, not DTO
        assertThat(result.getAccountId()).isEqualTo(accountId);
        assertThat(result.getMerchantId()).isEqualTo(merchantId);
        assertThat(result.getCategoryId()).isEqualTo(categoryId);
        assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
        assertThat(result.getTagIds()).containsExactly(tagId);
        // Amount is not locked — DTO wins
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("-99.99"));
    }

    @Test
    void createTransaction_noRecurringItem_noAccount_throws() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .merchantName("Kroger")
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .amount(new BigDecimal("-20.00"))
                .build();

        assertThatThrownBy(() -> transactionService.createTransaction(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Account ID must be provided");
    }

    @Test
    void createTransaction_noRecurringItem_noMerchant_throws() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .accountId(accountId)
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .amount(new BigDecimal("-20.00"))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());

        assertThatThrownBy(() -> transactionService.createTransaction(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Merchant name must be provided");
    }

    @Test
    void createTransaction_noRecurringItem_noAmount_throws() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .accountId(accountId)
                .merchantName("Kroger")
                .date(LocalDateTime.of(2025, 7, 1, 0, 0))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantService.resolveMerchant("Kroger", workspaceId)).thenReturn(buildMerchant("Kroger"));

        assertThatThrownBy(() -> transactionService.createTransaction(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Amount must be provided");
    }

    // --- updateTransaction with recurringItemId inheritance ---

    @Test
    void updateTransaction_setRecurringItemId_inheritsDefaults() {
        Transaction txn = buildTransaction();
        txn.setCategoryId(null);
        txn.setTagIds(new HashSet<>());

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(recurringItemService.getRecurringItem(recurringItemId, workspaceId)).thenReturn(buildRecurringItem());
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = new UpdateTransactionDto();
        dto.assignRecurringItemId(recurringItemId);
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getRecurringItemId()).isEqualTo(recurringItemId);
        assertThat(result.getAccountId()).isEqualTo(accountId);
        assertThat(result.getMerchantId()).isEqualTo(merchantId);
        assertThat(result.getCategoryId()).isEqualTo(categoryId);
        assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("-15.99"));
        assertThat(result.getTagIds()).containsExactly(tagId);
    }

    @Test
    void updateTransaction_setRecurringItemId_lockedFieldsFromRI() {
        Transaction txn = buildTransaction();
        // Transaction has its own values that should be overwritten
        UUID originalCategoryId = UUID.randomUUID();
        txn.setCategoryId(originalCategoryId);
        txn.setTagIds(new HashSet<>(Set.of(UUID.randomUUID())));
        txn.setCurrencyCode(CurrencyCode.GBP);

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(recurringItemService.getRecurringItem(recurringItemId, workspaceId)).thenReturn(buildRecurringItem());
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = new UpdateTransactionDto();
        dto.assignRecurringItemId(recurringItemId);
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        // Locked fields come from RI, overwriting existing values
        assertThat(result.getRecurringItemId()).isEqualTo(recurringItemId);
        assertThat(result.getAccountId()).isEqualTo(accountId);
        assertThat(result.getMerchantId()).isEqualTo(merchantId);
        assertThat(result.getCategoryId()).isEqualTo(categoryId);
        assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
        assertThat(result.getTagIds()).containsExactly(tagId);
    }

    @Test
    void updateTransaction_clearRecurringItemId() {
        Transaction txn = buildTransaction();
        txn.setRecurringItemId(recurringItemId);

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = new UpdateTransactionDto();
        dto.assignRecurringItemId(null);
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getRecurringItemId()).isNull();
    }

    @Test
    void updateTransaction_recurringItemIdNotSpecified_doesNotChange() {
        Transaction txn = buildTransaction();
        txn.setRecurringItemId(recurringItemId);

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);

        assertThat(result.getRecurringItemId()).isEqualTo(recurringItemId);
    }

    // --- Field locking: recurring item ---

    @Test
    void updateTransaction_recurringLinked_rejectsCategoryId() {
        Transaction txn = buildTransaction();
        txn.setRecurringItemId(recurringItemId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignCategoryId(UUID.randomUUID());

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("categoryId")
                .hasMessageContaining("recurring item");
    }

    @Test
    void updateTransaction_recurringLinked_rejectsTagIds() {
        Transaction txn = buildTransaction();
        txn.setRecurringItemId(recurringItemId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignTagIds(Set.of(UUID.randomUUID()));

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tagIds")
                .hasMessageContaining("recurring item");
    }

    @Test
    void updateTransaction_recurringLinked_rejectsNotes() {
        Transaction txn = buildTransaction();
        txn.setRecurringItemId(recurringItemId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignNotes("new notes");

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("notes")
                .hasMessageContaining("recurring item");
    }

    @Test
    void updateTransaction_recurringLinked_rejectsMerchantName() {
        Transaction txn = buildTransaction();
        txn.setRecurringItemId(recurringItemId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().merchantName("New Merchant").build();

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("merchantName")
                .hasMessageContaining("recurring item");
    }

    @Test
    void updateTransaction_recurringLinked_rejectsMerchantId() {
        Transaction txn = buildTransaction();
        txn.setRecurringItemId(recurringItemId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignMerchantId(UUID.randomUUID());

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("merchantName")
                .hasMessageContaining("recurring item");
    }

    @Test
    void updateTransaction_recurringLinked_rejectsAccountId() {
        Transaction txn = buildTransaction();
        txn.setRecurringItemId(recurringItemId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().accountId(UUID.randomUUID()).build();

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("accountId")
                .hasMessageContaining("recurring item");
    }

    @Test
    void updateTransaction_recurringLinked_rejectsMultipleLockedFields() {
        Transaction txn = buildTransaction();
        txn.setRecurringItemId(recurringItemId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().accountId(UUID.randomUUID()).merchantName("X").build();
        dto.assignCategoryId(UUID.randomUUID());

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("accountId")
                .hasMessageContaining("merchantName")
                .hasMessageContaining("categoryId");
    }

    @Test
    void updateTransaction_recurringLinked_allowsUnlinkWithLockedFields() {
        Transaction txn = buildTransaction();
        txn.setRecurringItemId(recurringItemId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categoryService.getCategory(any(), eq(workspaceId))).thenReturn(buildCategory());

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignRecurringItemId(null); // unlink
        dto.assignCategoryId(UUID.randomUUID()); // allowed because unlinking

        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);
        assertThat(result.getRecurringItemId()).isNull();
    }

    @Test
    void updateTransaction_recurringLinked_allowsNonLockedFields() {
        Transaction txn = buildTransaction();
        txn.setRecurringItemId(recurringItemId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder()
                .amount(new BigDecimal("-99.00"))
                .status(TransactionStatus.POSTED)
                .build();

        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("-99.00"));
    }

    // --- Field locking: group ---

    @Test
    void updateTransaction_grouped_rejectsCategoryId() {
        Transaction txn = buildTransaction();
        txn.setGroupId(UUID.randomUUID());
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignCategoryId(UUID.randomUUID());

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("categoryId")
                .hasMessageContaining("group");
    }

    @Test
    void updateTransaction_grouped_rejectsTagIds() {
        Transaction txn = buildTransaction();
        txn.setGroupId(UUID.randomUUID());
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignTagIds(Set.of(UUID.randomUUID()));

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tagIds")
                .hasMessageContaining("group");
    }

    @Test
    void updateTransaction_grouped_rejectsNotes() {
        Transaction txn = buildTransaction();
        txn.setGroupId(UUID.randomUUID());
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignNotes("new notes");

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("notes")
                .hasMessageContaining("group");
    }

    @Test
    void updateTransaction_grouped_allowsNonLockedFields() {
        Transaction txn = buildTransaction();
        txn.setGroupId(UUID.randomUUID());
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(accountService.getAccount(any(), eq(workspaceId))).thenReturn(buildAccount());
        when(merchantService.resolveMerchant("New Place", workspaceId)).thenReturn(buildMerchant("New Place"));

        UpdateTransactionDto dto = UpdateTransactionDto.builder()
                .accountId(accountId)
                .merchantName("New Place")
                .amount(new BigDecimal("-99.00"))
                .build();

        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("-99.00"));
    }

    // --- Mutual exclusivity ---

    @Test
    void updateTransaction_grouped_rejectsLinkingRecurringItem() {
        Transaction txn = buildTransaction();
        txn.setGroupId(UUID.randomUUID());
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignRecurringItemId(recurringItemId);

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("group")
                .hasMessageContaining("Remove it from the group");
    }

    // --- Unlink from group via groupId: null ---

    @Test
    void updateTransaction_unlinkFromGroup_succeeds() {
        UUID groupId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setGroupId(groupId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.countByGroupId(groupId)).thenReturn(3L);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignGroupId(null);

        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);
        assertThat(result.getGroupId()).isNull();
    }

    @Test
    void updateTransaction_unlinkFromGroup_rejectsWhenWouldLeaveFewerThan2() {
        UUID groupId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setGroupId(groupId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.countByGroupId(groupId)).thenReturn(2L);

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignGroupId(null);

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("fewer than 2");
    }

    @Test
    void updateTransaction_unlinkFromGroup_allowsLockedFieldsInSameRequest() {
        UUID groupId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setGroupId(groupId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.countByGroupId(groupId)).thenReturn(3L);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categoryService.getCategory(any(), eq(workspaceId))).thenReturn(buildCategory());

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignGroupId(null);
        dto.assignCategoryId(UUID.randomUUID());

        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);
        assertThat(result.getGroupId()).isNull();
    }

    @Test
    void updateTransaction_assignGroupIdViaUpdate_throws() {
        Transaction txn = buildTransaction();
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignGroupId(UUID.randomUUID());

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("transaction-groups API");
    }

    @Test
    void updateTransaction_unlinkGroupAndLinkRecurring_succeeds() {
        UUID groupId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setGroupId(groupId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.countByGroupId(groupId)).thenReturn(3L);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(recurringItemService.getRecurringItem(recurringItemId, workspaceId)).thenReturn(buildRecurringItem());

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignGroupId(null);
        dto.assignRecurringItemId(recurringItemId);

        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);
        assertThat(result.getGroupId()).isNull();
        assertThat(result.getRecurringItemId()).isEqualTo(recurringItemId);
    }

    // ===== Split field locking tests =====

    @Test
    void updateTransaction_splitLocked_rejectsAccountId() {
        UUID splitId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setSplitId(splitId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().accountId(UUID.randomUUID()).build();

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("accountId")
                .hasMessageContaining("transaction-splits API");
    }

    @Test
    void updateTransaction_splitLocked_rejectsCurrencyCode() {
        UUID splitId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setSplitId(splitId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().currencyCode(CurrencyCode.EUR).build();

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("currencyCode")
                .hasMessageContaining("transaction-splits API");
    }

    @Test
    void updateTransaction_splitLocked_rejectsAmount() {
        UUID splitId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setSplitId(splitId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().amount(new BigDecimal("99.99")).build();

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("amount")
                .hasMessageContaining("transaction-splits API");
    }

    @Test
    void updateTransaction_splitLocked_rejectsDate() {
        UUID splitId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setSplitId(splitId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().date(LocalDateTime.now()).build();

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("date")
                .hasMessageContaining("transaction-splits API");
    }

    @Test
    void updateTransaction_splitLocked_allowsMerchantName() {
        UUID splitId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setSplitId(splitId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(merchantService.resolveMerchant("Walmart", workspaceId)).thenReturn(buildMerchant("Walmart"));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().merchantName("Walmart").build();

        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);
        assertThat(result.getMerchantId()).isEqualTo(merchantId);
    }

    @Test
    void updateTransaction_splitLocked_allowsCategoryId() {
        UUID splitId = UUID.randomUUID();
        UUID newCatId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setSplitId(splitId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categoryService.getCategory(newCatId, workspaceId)).thenReturn(Category.builder().id(newCatId).build());

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignCategoryId(newCatId);

        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);
        assertThat(result.getCategoryId()).isEqualTo(newCatId);
    }

    @Test
    void updateTransaction_splitLocked_allowsTagIds() {
        UUID splitId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setSplitId(splitId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tagService.getTag(tagId, workspaceId)).thenReturn(buildTag());

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignTagIds(Set.of(tagId));

        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);
        assertThat(result.getTagIds()).containsExactly(tagId);
    }

    @Test
    void updateTransaction_splitLocked_allowsNotes() {
        UUID splitId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setSplitId(splitId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignNotes("Updated notes");

        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);
        assertThat(result.getNotes()).isEqualTo("Updated notes");
    }

    // ===== Split locked tests =====

    @Test
    void updateTransaction_unlinkSplit_throws() {
        UUID splitId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setSplitId(splitId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignSplitId(null);

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("transaction-splits API");
    }

    @Test
    void updateTransaction_assignSplitId_throws() {
        Transaction txn = buildTransaction();
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignSplitId(UUID.randomUUID());

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("transaction-splits API");
    }

    // ===== Mutual exclusivity: split + group =====

    @Test
    void updateTransaction_splitChild_assignGroup_throws() {
        UUID splitId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setSplitId(splitId);
        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignGroupId(UUID.randomUUID());

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("split")
                .hasMessageContaining("group");
    }

    // ===== Split child + RI linking =====

    @Test
    void updateTransaction_splitChild_linkRI_success() {
        UUID splitId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setSplitId(splitId);

        TransactionSplit split = TransactionSplit.builder()
                .id(splitId)
                .workspaceId(workspaceId)
                .accountId(accountId)
                .totalAmount(new BigDecimal("100.00"))
                .currencyCode(CurrencyCode.EUR)
                .build();

        RecurringItem ri = buildRecurringItem();
        // RI accountId and currencyCode must match the split
        ri.setAccountId(accountId);
        ri.setCurrencyCode(CurrencyCode.EUR);

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(recurringItemService.getRecurringItem(recurringItemId, workspaceId)).thenReturn(ri);
        when(transactionSplitRepository.findByIdAndWorkspaceId(splitId, workspaceId)).thenReturn(Optional.of(split));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignRecurringItemId(recurringItemId);

        Transaction result = transactionService.updateTransaction(transactionId, workspaceId, dto);
        assertThat(result.getRecurringItemId()).isEqualTo(recurringItemId);
    }

    @Test
    void updateTransaction_splitChild_linkRI_accountMismatch_throws() {
        UUID splitId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setSplitId(splitId);

        TransactionSplit split = TransactionSplit.builder()
                .id(splitId)
                .workspaceId(workspaceId)
                .accountId(UUID.randomUUID()) // different account
                .totalAmount(new BigDecimal("100.00"))
                .currencyCode(CurrencyCode.USD)
                .build();

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(recurringItemService.getRecurringItem(recurringItemId, workspaceId)).thenReturn(buildRecurringItem());
        when(transactionSplitRepository.findByIdAndWorkspaceId(splitId, workspaceId)).thenReturn(Optional.of(split));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignRecurringItemId(recurringItemId);

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("account");
    }

    @Test
    void updateTransaction_splitChild_linkRI_currencyMismatch_throws() {
        UUID splitId = UUID.randomUUID();
        Transaction txn = buildTransaction();
        txn.setSplitId(splitId);

        TransactionSplit split = TransactionSplit.builder()
                .id(splitId)
                .workspaceId(workspaceId)
                .accountId(accountId)
                .totalAmount(new BigDecimal("100.00"))
                .currencyCode(CurrencyCode.USD) // split is USD
                .build();

        RecurringItem ri = buildRecurringItem();
        ri.setAccountId(accountId);
        ri.setCurrencyCode(CurrencyCode.EUR); // RI is EUR — mismatch

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(recurringItemService.getRecurringItem(recurringItemId, workspaceId)).thenReturn(ri);
        when(transactionSplitRepository.findByIdAndWorkspaceId(splitId, workspaceId)).thenReturn(Optional.of(split));

        UpdateTransactionDto dto = UpdateTransactionDto.builder().build();
        dto.assignRecurringItemId(recurringItemId);

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("currency");
    }

    // --- Category Polarity Validation ---

    @Test
    void createTransaction_polarityMismatch_throws() {
        CreateTransactionDto dto = CreateTransactionDto.builder()
                .accountId(accountId)
                .merchantName("Target")
                .amount(new BigDecimal("100.00"))
                .categoryId(categoryId)
                .date(LocalDateTime.now())
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantService.resolveMerchant("Target", workspaceId)).thenReturn(buildMerchant("Target"));
        when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(buildCategory());
        doThrow(new BadRequestException("Positive amounts must use an income category"))
                .when(categoryService).validateCategoryPolarity(categoryId, new BigDecimal("100.00"), workspaceId);

        assertThatThrownBy(() -> transactionService.createTransaction(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("income category");
    }

    @Test
    void updateTransaction_changeCategoryPolarityMismatch_throws() {
        UUID newCatId = UUID.randomUUID();
        Transaction txn = Transaction.builder()
                .id(transactionId)
                .workspaceId(workspaceId)
                .accountId(accountId)
                .merchantId(merchantId)
                .amount(new BigDecimal("-50.00"))
                .currencyCode(CurrencyCode.USD)
                .status(TransactionStatus.PENDING)
                .source(TransactionSource.MANUAL)
                .build();

        Category newCat = Category.builder().id(newCatId).workspaceId(workspaceId).name("Salary").income(true).build();

        UpdateTransactionDto dto = new UpdateTransactionDto();
        dto.assignCategoryId(newCatId);

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        when(categoryService.getCategory(newCatId, workspaceId)).thenReturn(newCat);
        doThrow(new BadRequestException("Negative amounts must use an expense category"))
                .when(categoryService).validateCategoryPolarity(newCatId, txn.getAmount(), workspaceId);

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expense category");
    }

    @Test
    void updateTransaction_changeAmountPolarityMismatch_throws() {
        Transaction txn = Transaction.builder()
                .id(transactionId)
                .workspaceId(workspaceId)
                .accountId(accountId)
                .merchantId(merchantId)
                .categoryId(categoryId)
                .amount(new BigDecimal("-50.00"))
                .currencyCode(CurrencyCode.USD)
                .status(TransactionStatus.PENDING)
                .source(TransactionSource.MANUAL)
                .build();

        UpdateTransactionDto dto = UpdateTransactionDto.builder().amount(new BigDecimal("100.00")).build();

        when(transactionRepository.findByIdAndWorkspaceId(transactionId, workspaceId)).thenReturn(Optional.of(txn));
        doThrow(new BadRequestException("Positive amounts must use an income category"))
                .when(categoryService).validateCategoryPolarity(categoryId, new BigDecimal("100.00"), workspaceId);

        assertThatThrownBy(() -> transactionService.updateTransaction(transactionId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("income category");
    }

}
