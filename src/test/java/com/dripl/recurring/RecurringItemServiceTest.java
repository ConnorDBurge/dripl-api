package com.dripl.recurring;

import com.dripl.account.entity.Account;
import com.dripl.account.enums.CurrencyCode;
import com.dripl.account.service.AccountService;
import com.dripl.category.entity.Category;
import com.dripl.category.service.CategoryService;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.common.enums.Status;
import com.dripl.merchant.entity.Merchant;
import com.dripl.merchant.service.MerchantService;
import com.dripl.recurring.dto.CreateRecurringItemDto;
import com.dripl.recurring.dto.UpdateRecurringItemDto;
import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.enums.FrequencyGranularity;
import com.dripl.recurring.enums.RecurringItemStatus;
import com.dripl.recurring.mapper.RecurringItemMapper;
import com.dripl.recurring.repository.RecurringItemRepository;
import com.dripl.recurring.service.RecurringItemService;
import com.dripl.tag.entity.Tag;
import com.dripl.tag.service.TagService;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringItemServiceTest {

    @Mock
    private RecurringItemRepository recurringItemRepository;
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

    @Spy
    private RecurringItemMapper recurringItemMapper = Mappers.getMapper(RecurringItemMapper.class);

    @InjectMocks
    private RecurringItemService recurringItemService;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID recurringItemId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID merchantId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID tagId = UUID.randomUUID();

    private RecurringItem buildRecurringItem() {
        return RecurringItem.builder()
                .id(recurringItemId)
                .workspaceId(workspaceId)
                .accountId(accountId)
                .merchantId(merchantId)
                .categoryId(categoryId)
                .description("Netflix")
                .amount(new BigDecimal("-15.99"))
                .currencyCode(CurrencyCode.USD)
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .frequencyQuantity(1)
                .anchorDates(new ArrayList<>(List.of(LocalDateTime.of(2025, 1, 15, 0, 0))))
                .startDate(LocalDateTime.of(2025, 1, 1, 0, 0))
                .status(RecurringItemStatus.ACTIVE)
                .tagIds(new HashSet<>())
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
        return Tag.builder().id(tagId).workspaceId(workspaceId).name("Subscription").status(Status.ACTIVE).build();
    }

    // --- listAllByWorkspaceId ---

    @Test
    void listAllByWorkspaceId_returnsItems() {
        when(recurringItemRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(buildRecurringItem()));

        List<RecurringItem> result = recurringItemService.listAllByWorkspaceId(workspaceId);

        assertThat(result).hasSize(1);
    }

    @Test
    void listAllByWorkspaceId_emptyList() {
        when(recurringItemRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of());

        List<RecurringItem> result = recurringItemService.listAllByWorkspaceId(workspaceId);

        assertThat(result).isEmpty();
    }

    // --- getRecurringItem ---

    @Test
    void getRecurringItem_found() {
        RecurringItem item = buildRecurringItem();
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));

        RecurringItem result = recurringItemService.getRecurringItem(recurringItemId, workspaceId);

        assertThat(result.getId()).isEqualTo(recurringItemId);
    }

    @Test
    void getRecurringItem_notFound_throws() {
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recurringItemService.getRecurringItem(recurringItemId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Recurring item not found");
    }

    // --- createRecurringItem ---

    @Test
    void createRecurringItem_success_existingMerchant() {
        CreateRecurringItemDto dto = CreateRecurringItemDto.builder()
                .accountId(accountId)
                .merchantName("Netflix")
                .categoryId(categoryId)
                .description("Netflix Subscription")
                .amount(new BigDecimal("-15.99"))
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .frequencyQuantity(1)
                .anchorDates(List.of(LocalDateTime.of(2025, 7, 1, 0, 0)))
                .startDate(LocalDateTime.of(2025, 7, 1, 0, 0))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantService.resolveMerchant("Netflix", workspaceId)).thenReturn(buildMerchant("Netflix"));
        when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(buildCategory());
        when(recurringItemRepository.save(any(RecurringItem.class))).thenAnswer(inv -> inv.getArgument(0));

        RecurringItem result = recurringItemService.createRecurringItem(workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(merchantId);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("-15.99"));
        assertThat(result.getStatus()).isEqualTo(RecurringItemStatus.ACTIVE);
        assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.USD);
    }

    @Test
    void createRecurringItem_success_autoCreatesMerchant() {
        CreateRecurringItemDto dto = CreateRecurringItemDto.builder()
                .accountId(accountId)
                .merchantName("New Service")
                .description("New Service Subscription")
                .amount(new BigDecimal("-10.00"))
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .frequencyQuantity(1)
                .anchorDates(List.of(LocalDateTime.of(2025, 7, 1, 0, 0)))
                .startDate(LocalDateTime.of(2025, 7, 1, 0, 0))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantService.resolveMerchant("New Service", workspaceId)).thenReturn(buildMerchant("New Service"));
        when(recurringItemRepository.save(any(RecurringItem.class))).thenAnswer(inv -> inv.getArgument(0));

        RecurringItem result = recurringItemService.createRecurringItem(workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(merchantId);
    }

    @Test
    void createRecurringItem_merchantLookup_caseInsensitive() {
        CreateRecurringItemDto dto = CreateRecurringItemDto.builder()
                .accountId(accountId)
                .merchantName("NETFLIX")
                .description("Netflix Subscription")
                .amount(new BigDecimal("-15.99"))
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .frequencyQuantity(1)
                .anchorDates(List.of(LocalDateTime.of(2025, 7, 1, 0, 0)))
                .startDate(LocalDateTime.of(2025, 7, 1, 0, 0))
                .build();

        Merchant existingMerchant = buildMerchant("Netflix");
        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantService.resolveMerchant("NETFLIX", workspaceId)).thenReturn(existingMerchant);
        when(recurringItemRepository.save(any(RecurringItem.class))).thenAnswer(inv -> inv.getArgument(0));

        RecurringItem result = recurringItemService.createRecurringItem(workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(merchantId);
    }

    @Test
    void createRecurringItem_withTags() {
        CreateRecurringItemDto dto = CreateRecurringItemDto.builder()
                .accountId(accountId)
                .merchantName("Netflix")
                .description("Netflix Subscription")
                .amount(new BigDecimal("-15.99"))
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .frequencyQuantity(1)
                .anchorDates(List.of(LocalDateTime.of(2025, 7, 1, 0, 0)))
                .startDate(LocalDateTime.of(2025, 7, 1, 0, 0))
                .tagIds(Set.of(tagId))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantService.resolveMerchant("Netflix", workspaceId)).thenReturn(buildMerchant("Netflix"));
        when(tagService.getTag(tagId, workspaceId)).thenReturn(buildTag());
        when(recurringItemRepository.save(any(RecurringItem.class))).thenAnswer(inv -> inv.getArgument(0));

        RecurringItem result = recurringItemService.createRecurringItem(workspaceId, dto);

        assertThat(result.getTagIds()).containsExactly(tagId);
    }

    @Test
    void createRecurringItem_withCurrencyCode() {
        CreateRecurringItemDto dto = CreateRecurringItemDto.builder()
                .accountId(accountId)
                .merchantName("Netflix")
                .description("Netflix Subscription")
                .amount(new BigDecimal("-15.99"))
                .currencyCode(CurrencyCode.EUR)
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .frequencyQuantity(1)
                .anchorDates(List.of(LocalDateTime.of(2025, 7, 1, 0, 0)))
                .startDate(LocalDateTime.of(2025, 7, 1, 0, 0))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantService.resolveMerchant("Netflix", workspaceId)).thenReturn(buildMerchant("Netflix"));
        when(recurringItemRepository.save(any(RecurringItem.class))).thenAnswer(inv -> inv.getArgument(0));

        RecurringItem result = recurringItemService.createRecurringItem(workspaceId, dto);

        assertThat(result.getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
    }

    @Test
    void createRecurringItem_accountNotInWorkspace_throws() {
        CreateRecurringItemDto dto = CreateRecurringItemDto.builder()
                .accountId(accountId)
                .merchantName("Netflix")
                .description("Netflix Subscription")
                .amount(new BigDecimal("-15.99"))
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .frequencyQuantity(1)
                .anchorDates(List.of(LocalDateTime.of(2025, 7, 1, 0, 0)))
                .startDate(LocalDateTime.of(2025, 7, 1, 0, 0))
                .build();

        when(accountService.getAccount(accountId, workspaceId))
                .thenThrow(new ResourceNotFoundException("Account not found"));

        assertThatThrownBy(() -> recurringItemService.createRecurringItem(workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void createRecurringItem_categoryNotInWorkspace_throws() {
        CreateRecurringItemDto dto = CreateRecurringItemDto.builder()
                .accountId(accountId)
                .merchantName("Netflix")
                .categoryId(categoryId)
                .description("Netflix Subscription")
                .amount(new BigDecimal("-15.99"))
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .frequencyQuantity(1)
                .anchorDates(List.of(LocalDateTime.of(2025, 7, 1, 0, 0)))
                .startDate(LocalDateTime.of(2025, 7, 1, 0, 0))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantService.resolveMerchant("Netflix", workspaceId)).thenReturn(buildMerchant("Netflix"));
        when(categoryService.getCategory(categoryId, workspaceId))
                .thenThrow(new ResourceNotFoundException("Category not found"));

        assertThatThrownBy(() -> recurringItemService.createRecurringItem(workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void createRecurringItem_tagNotInWorkspace_throws() {
        CreateRecurringItemDto dto = CreateRecurringItemDto.builder()
                .accountId(accountId)
                .merchantName("Netflix")
                .description("Netflix Subscription")
                .amount(new BigDecimal("-15.99"))
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .frequencyQuantity(1)
                .anchorDates(List.of(LocalDateTime.of(2025, 7, 1, 0, 0)))
                .startDate(LocalDateTime.of(2025, 7, 1, 0, 0))
                .tagIds(Set.of(tagId))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantService.resolveMerchant("Netflix", workspaceId)).thenReturn(buildMerchant("Netflix"));
        when(tagService.getTag(tagId, workspaceId))
                .thenThrow(new ResourceNotFoundException("Tag not found"));

        assertThatThrownBy(() -> recurringItemService.createRecurringItem(workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Tag");
    }

    @Test
    void createRecurringItem_groupCategory_throws() {
        CreateRecurringItemDto dto = CreateRecurringItemDto.builder()
                .accountId(accountId)
                .merchantName("Netflix")
                .categoryId(categoryId)
                .description("Netflix Subscription")
                .amount(new BigDecimal("-15.99"))
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .frequencyQuantity(1)
                .anchorDates(List.of(LocalDateTime.of(2025, 7, 1, 0, 0)))
                .startDate(LocalDateTime.of(2025, 7, 1, 0, 0))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantService.resolveMerchant("Netflix", workspaceId)).thenReturn(buildMerchant("Netflix"));
        when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(buildCategory());
        doThrow(new BadRequestException("Cannot assign a parent category group"))
                .when(categoryService).validateNotGroup(categoryId);

        assertThatThrownBy(() -> recurringItemService.createRecurringItem(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("parent category group");
    }

    @Test
    void createRecurringItem_noCategoryId_success() {
        CreateRecurringItemDto dto = CreateRecurringItemDto.builder()
                .accountId(accountId)
                .merchantName("Netflix")
                .description("Netflix Subscription")
                .amount(new BigDecimal("-15.99"))
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .frequencyQuantity(1)
                .anchorDates(List.of(LocalDateTime.of(2025, 7, 1, 0, 0)))
                .startDate(LocalDateTime.of(2025, 7, 1, 0, 0))
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantService.resolveMerchant("Netflix", workspaceId)).thenReturn(buildMerchant("Netflix"));
        when(recurringItemRepository.save(any(RecurringItem.class))).thenAnswer(inv -> inv.getArgument(0));

        RecurringItem result = recurringItemService.createRecurringItem(workspaceId, dto);

        assertThat(result.getCategoryId()).isNull();
        verify(categoryService, never()).getCategory(any(), any());
    }

    // --- updateRecurringItem ---

    @Test
    void updateRecurringItem_account() {
        RecurringItem item = buildRecurringItem();
        UUID newAccountId = UUID.randomUUID();
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(accountService.getAccount(newAccountId, workspaceId))
                .thenReturn(Account.builder().id(newAccountId).workspaceId(workspaceId).build());
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = UpdateRecurringItemDto.builder().accountId(newAccountId).build();
        RecurringItem result = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(result.getAccountId()).isEqualTo(newAccountId);
    }

    @Test
    void updateRecurringItem_merchantName_existingMerchant() {
        RecurringItem item = buildRecurringItem();
        UUID newMerchantId = UUID.randomUUID();
        Merchant existingMerchant = Merchant.builder().id(newMerchantId).workspaceId(workspaceId).name("Spotify").build();

        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(merchantService.resolveMerchant("Spotify", workspaceId)).thenReturn(existingMerchant);
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = UpdateRecurringItemDto.builder().merchantName("Spotify").build();
        RecurringItem result = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(newMerchantId);
    }

    @Test
    void updateRecurringItem_merchantName_autoCreates() {
        RecurringItem item = buildRecurringItem();
        UUID newMerchantId = UUID.randomUUID();
        Merchant newMerchant = Merchant.builder().id(newMerchantId).workspaceId(workspaceId).name("New Service").build();

        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(merchantService.resolveMerchant("New Service", workspaceId)).thenReturn(newMerchant);
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = UpdateRecurringItemDto.builder().merchantName("New Service").build();
        RecurringItem result = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(result.getMerchantId()).isEqualTo(newMerchantId);
    }

    @Test
    void updateRecurringItem_setCategoryId() {
        RecurringItem item = buildRecurringItem();
        item.setCategoryId(null);
        UUID newCategoryId = UUID.randomUUID();

        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(categoryService.getCategory(newCategoryId, workspaceId))
                .thenReturn(Category.builder().id(newCategoryId).workspaceId(workspaceId).build());
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = new UpdateRecurringItemDto();
        dto.assignCategoryId(newCategoryId);
        RecurringItem result = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(result.getCategoryId()).isEqualTo(newCategoryId);
    }

    @Test
    void updateRecurringItem_groupCategory_throws() {
        RecurringItem item = buildRecurringItem();
        UUID groupCategoryId = UUID.randomUUID();

        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(categoryService.getCategory(groupCategoryId, workspaceId))
                .thenReturn(Category.builder().id(groupCategoryId).workspaceId(workspaceId).build());
        doThrow(new BadRequestException("Cannot assign a parent category group"))
                .when(categoryService).validateNotGroup(groupCategoryId);

        UpdateRecurringItemDto dto = new UpdateRecurringItemDto();
        dto.assignCategoryId(groupCategoryId);

        assertThatThrownBy(() -> recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("parent category group");
    }

    @Test
    void updateRecurringItem_clearCategoryId() {
        RecurringItem item = buildRecurringItem();
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = new UpdateRecurringItemDto();
        dto.assignCategoryId(null);
        RecurringItem result = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(result.getCategoryId()).isNull();
    }

    @Test
    void updateRecurringItem_categoryIdNotSpecified_doesNotChange() {
        RecurringItem item = buildRecurringItem();
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = UpdateRecurringItemDto.builder().build();
        RecurringItem result = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(result.getCategoryId()).isEqualTo(categoryId);
    }

    @Test
    void updateRecurringItem_description() {
        RecurringItem item = buildRecurringItem();
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = UpdateRecurringItemDto.builder().description("Spotify Premium").build();
        RecurringItem result = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(result.getDescription()).isEqualTo("Spotify Premium");
    }

    @Test
    void updateRecurringItem_amount() {
        RecurringItem item = buildRecurringItem();
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = UpdateRecurringItemDto.builder().amount(new BigDecimal("-99.99")).build();
        RecurringItem result = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("-99.99"));
    }

    @Test
    void updateRecurringItem_notes_set() {
        RecurringItem item = buildRecurringItem();
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = new UpdateRecurringItemDto();
        dto.assignNotes("Monthly streaming");
        RecurringItem result = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(result.getNotes()).isEqualTo("Monthly streaming");
    }

    @Test
    void updateRecurringItem_notes_clear() {
        RecurringItem item = buildRecurringItem();
        item.setNotes("Old notes");
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = new UpdateRecurringItemDto();
        dto.assignNotes(null);
        RecurringItem result = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(result.getNotes()).isNull();
    }

    @Test
    void updateRecurringItem_tags_set() {
        RecurringItem item = buildRecurringItem();
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(tagService.getTag(tagId, workspaceId)).thenReturn(buildTag());
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = new UpdateRecurringItemDto();
        dto.assignTagIds(Set.of(tagId));
        RecurringItem result = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(result.getTagIds()).containsExactly(tagId);
    }

    @Test
    void updateRecurringItem_tags_clear() {
        RecurringItem item = buildRecurringItem();
        item.setTagIds(Set.of(tagId));
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = new UpdateRecurringItemDto();
        dto.assignTagIds(Set.of());
        RecurringItem result = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(result.getTagIds()).isEmpty();
    }

    @Test
    void updateRecurringItem_tagsNotSpecified_doesNotChange() {
        RecurringItem item = buildRecurringItem();
        item.setTagIds(Set.of(tagId));
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = UpdateRecurringItemDto.builder().build();
        RecurringItem result = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(result.getTagIds()).containsExactly(tagId);
    }

    @Test
    void updateRecurringItem_endDate_set() {
        RecurringItem item = buildRecurringItem();
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LocalDateTime endDate = LocalDateTime.of(2026, 1, 1, 0, 0);
        UpdateRecurringItemDto dto = new UpdateRecurringItemDto();
        dto.assignEndDate(endDate);
        RecurringItem result = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(result.getEndDate()).isEqualTo(endDate);
    }

    @Test
    void updateRecurringItem_endDate_clear() {
        RecurringItem item = buildRecurringItem();
        item.setEndDate(LocalDateTime.of(2026, 1, 1, 0, 0));
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = new UpdateRecurringItemDto();
        dto.assignEndDate(null);
        RecurringItem result = recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(result.getEndDate()).isNull();
    }

    // --- deleteRecurringItem ---

    @Test
    void deleteRecurringItem_success() {
        RecurringItem item = buildRecurringItem();
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(item));

        recurringItemService.deleteRecurringItem(recurringItemId, workspaceId);

        verify(recurringItemRepository).delete(item);
    }

    @Test
    void deleteRecurringItem_notFound_throws() {
        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recurringItemService.deleteRecurringItem(recurringItemId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(recurringItemRepository, never()).delete(any());
    }

    // --- Category Polarity Validation ---

    @Test
    void createRecurringItem_polarityMismatch_throws() {
        CreateRecurringItemDto dto = CreateRecurringItemDto.builder()
                .accountId(accountId)
                .merchantName("Netflix")
                .amount(new BigDecimal("-15.99"))
                .categoryId(categoryId)
                .description("Netflix")
                .frequencyGranularity(FrequencyGranularity.MONTH)
                .anchorDates(List.of(LocalDateTime.now()))
                .startDate(LocalDateTime.now())
                .build();

        when(accountService.getAccount(accountId, workspaceId)).thenReturn(buildAccount());
        when(merchantService.resolveMerchant("Netflix", workspaceId)).thenReturn(buildMerchant("Netflix"));
        when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(buildCategory());
        doThrow(new BadRequestException("Negative amounts must use an expense category"))
                .when(categoryService).validateCategoryPolarity(categoryId, dto.getAmount(), workspaceId);

        assertThatThrownBy(() -> recurringItemService.createRecurringItem(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expense category");
    }

    @Test
    void updateRecurringItem_changeCategoryPolarityMismatch_throws() {
        UUID newCatId = UUID.randomUUID();
        RecurringItem ri = buildRecurringItem();

        UpdateRecurringItemDto dto = new UpdateRecurringItemDto();
        dto.assignCategoryId(newCatId);

        Category newCat = Category.builder().id(newCatId).workspaceId(workspaceId).name("Salary").income(true).build();

        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(ri));
        when(categoryService.getCategory(newCatId, workspaceId)).thenReturn(newCat);
        doThrow(new BadRequestException("Negative amounts must use an expense category"))
                .when(categoryService).validateCategoryPolarity(eq(newCatId), any(BigDecimal.class), eq(workspaceId));

        assertThatThrownBy(() -> recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expense category");
    }

    @Test
    void updateRecurringItem_changeAmountPolarityMismatch_throws() {
        RecurringItem ri = buildRecurringItem();
        ri.setCategoryId(categoryId);

        UpdateRecurringItemDto dto = UpdateRecurringItemDto.builder().amount(new BigDecimal("100.00")).build();

        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(ri));
        doThrow(new BadRequestException("Positive amounts must use an income category"))
                .when(categoryService).validateCategoryPolarity(eq(categoryId), any(BigDecimal.class), eq(workspaceId));

        assertThatThrownBy(() -> recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("income category");
    }

    // ── Cascade to Linked Transactions ───────────────────────────────

    @Test
    void updateRecurringItem_cascadesCategoryToLinkedTransactions() {
        RecurringItem ri = buildRecurringItem();
        UUID newCategoryId = UUID.randomUUID();
        Transaction txn1 = Transaction.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                .accountId(accountId).merchantId(merchantId).amount(new BigDecimal("-15.99"))
                .recurringItemId(recurringItemId).categoryId(categoryId).tagIds(new HashSet<>()).build();
        Transaction txn2 = Transaction.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                .accountId(accountId).merchantId(merchantId).amount(new BigDecimal("-15.99"))
                .recurringItemId(recurringItemId).categoryId(categoryId).tagIds(new HashSet<>()).build();

        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(ri));
        when(categoryService.getCategory(newCategoryId, workspaceId))
                .thenReturn(Category.builder().id(newCategoryId).workspaceId(workspaceId).build());
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.findAllByRecurringItemIdAndWorkspaceId(recurringItemId, workspaceId))
                .thenReturn(List.of(txn1, txn2));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = UpdateRecurringItemDto.builder().build();
        dto.assignCategoryId(newCategoryId);
        recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(txn1.getCategoryId()).isEqualTo(newCategoryId);
        assertThat(txn2.getCategoryId()).isEqualTo(newCategoryId);
        verify(transactionRepository, times(2)).save(any());
    }

    @Test
    void updateRecurringItem_cascadesAllLockedFieldsToLinkedTransactions() {
        RecurringItem ri = buildRecurringItem();
        UUID newAccountId = UUID.randomUUID();
        UUID newMerchantId = UUID.randomUUID();
        UUID newTagId = UUID.randomUUID();
        Transaction txn = Transaction.builder().id(UUID.randomUUID()).workspaceId(workspaceId)
                .accountId(accountId).merchantId(merchantId).amount(new BigDecimal("-15.99"))
                .recurringItemId(recurringItemId).categoryId(categoryId)
                .currencyCode(CurrencyCode.USD).tagIds(new HashSet<>()).build();

        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(ri));
        when(accountService.getAccount(newAccountId, workspaceId))
                .thenReturn(Account.builder().id(newAccountId).workspaceId(workspaceId).build());
        when(merchantService.resolveMerchant("NewMerch", workspaceId))
                .thenReturn(Merchant.builder().id(newMerchantId).workspaceId(workspaceId).name("NewMerch").status(Status.ACTIVE).build());
        when(tagService.getTag(newTagId, workspaceId))
                .thenReturn(Tag.builder().id(newTagId).workspaceId(workspaceId).name("New Tag").build());
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.findAllByRecurringItemIdAndWorkspaceId(recurringItemId, workspaceId))
                .thenReturn(List.of(txn));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRecurringItemDto dto = UpdateRecurringItemDto.builder()
                .accountId(newAccountId)
                .merchantName("NewMerch")
                .currencyCode(CurrencyCode.EUR)
                .build();
        dto.assignNotes("new notes");
        dto.assignTagIds(Set.of(newTagId));
        recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        assertThat(txn.getAccountId()).isEqualTo(newAccountId);
        assertThat(txn.getMerchantId()).isEqualTo(newMerchantId);
        assertThat(txn.getCurrencyCode()).isEqualTo(CurrencyCode.EUR);
        assertThat(txn.getNotes()).isEqualTo("new notes");
        assertThat(txn.getTagIds()).containsExactly(newTagId);
    }

    @Test
    void updateRecurringItem_noLinkedTransactions_noCascade() {
        RecurringItem ri = buildRecurringItem();

        when(recurringItemRepository.findByIdAndWorkspaceId(recurringItemId, workspaceId)).thenReturn(Optional.of(ri));
        when(recurringItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.findAllByRecurringItemIdAndWorkspaceId(recurringItemId, workspaceId))
                .thenReturn(List.of());

        UpdateRecurringItemDto dto = UpdateRecurringItemDto.builder().build();
        dto.assignNotes("updated");
        recurringItemService.updateRecurringItem(recurringItemId, workspaceId, dto);

        verify(transactionRepository, never()).save(any());
    }
}
