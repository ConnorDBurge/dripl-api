package com.dripl.transaction.group;

import com.dripl.category.entity.Category;
import com.dripl.category.service.CategoryService;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ResourceNotFoundException;
import com.dripl.tag.entity.Tag;
import com.dripl.tag.service.TagService;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.group.dto.CreateTransactionGroupDto;
import com.dripl.transaction.group.dto.UpdateTransactionGroupDto;
import com.dripl.transaction.group.entity.TransactionGroup;
import com.dripl.transaction.group.repository.TransactionGroupRepository;
import com.dripl.transaction.group.service.TransactionGroupService;
import com.dripl.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
class TransactionGroupServiceTest {

    @Mock private TransactionGroupRepository transactionGroupRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private CategoryService categoryService;
    @Mock private TagService tagService;

    @InjectMocks
    private TransactionGroupService transactionGroupService;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private final UUID txn1Id = UUID.randomUUID();
    private final UUID txn2Id = UUID.randomUUID();
    private final UUID txn3Id = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID tagId = UUID.randomUUID();

    private TransactionGroup buildGroup() {
        return TransactionGroup.builder()
                .id(groupId)
                .workspaceId(workspaceId)
                .name("Beach Vacation 2025")
                .build();
    }

    private Transaction buildTransaction(UUID id, UUID existingGroupId) {
        return Transaction.builder()
                .id(id)
                .workspaceId(workspaceId)
                .amount(new BigDecimal("-50.00"))
                .groupId(existingGroupId)
                .build();
    }

    // --- listAllByWorkspaceId ---

    @Test
    void listAllByWorkspaceId_returnsList() {
        when(transactionGroupRepository.findAllByWorkspaceId(workspaceId))
                .thenReturn(List.of(buildGroup()));

        List<TransactionGroup> result = transactionGroupService.listAllByWorkspaceId(workspaceId);

        assertThat(result).hasSize(1);
    }

    // --- getTransactionGroup ---

    @Test
    void getTransactionGroup_found() {
        when(transactionGroupRepository.findByIdAndWorkspaceId(groupId, workspaceId))
                .thenReturn(Optional.of(buildGroup()));

        TransactionGroup result = transactionGroupService.getTransactionGroup(groupId, workspaceId);

        assertThat(result.getName()).isEqualTo("Beach Vacation 2025");
    }

    @Test
    void getTransactionGroup_notFound_throws() {
        when(transactionGroupRepository.findByIdAndWorkspaceId(groupId, workspaceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionGroupService.getTransactionGroup(groupId, workspaceId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- createTransactionGroup ---

    @Test
    void createTransactionGroup_success() {
        CreateTransactionGroupDto dto = CreateTransactionGroupDto.builder()
                .name("Beach Vacation 2025")
                .categoryId(categoryId)
                .tagIds(Set.of(tagId))
                .transactionIds(Set.of(txn1Id, txn2Id))
                .build();

        when(categoryService.getCategory(categoryId, workspaceId))
                .thenReturn(Category.builder().id(categoryId).build());
        when(tagService.getTag(tagId, workspaceId))
                .thenReturn(Tag.builder().id(tagId).build());
        when(transactionRepository.findByIdAndWorkspaceId(txn1Id, workspaceId))
                .thenReturn(Optional.of(buildTransaction(txn1Id, null)));
        when(transactionRepository.findByIdAndWorkspaceId(txn2Id, workspaceId))
                .thenReturn(Optional.of(buildTransaction(txn2Id, null)));
        when(transactionGroupRepository.save(any(TransactionGroup.class)))
                .thenAnswer(inv -> {
                    TransactionGroup g = inv.getArgument(0);
                    g.setId(groupId);
                    return g;
                });
        when(transactionRepository.setGroupId(eq(groupId), eq(Set.of(txn1Id, txn2Id)), eq(workspaceId)))
                .thenReturn(2);

        TransactionGroup result = transactionGroupService.createTransactionGroup(workspaceId, dto);

        assertThat(result.getName()).isEqualTo("Beach Vacation 2025");
        assertThat(result.getCategoryId()).isEqualTo(categoryId);
        assertThat(result.getTagIds()).containsExactly(tagId);
        verify(transactionRepository).setGroupId(groupId, Set.of(txn1Id, txn2Id), workspaceId);
    }

    @Test
    void createTransactionGroup_transactionAlreadyGrouped_throws() {
        CreateTransactionGroupDto dto = CreateTransactionGroupDto.builder()
                .name("Trip")
                .transactionIds(Set.of(txn1Id, txn2Id))
                .build();

        // Mock both — set order is unpredictable
        when(transactionRepository.findByIdAndWorkspaceId(any(UUID.class), eq(workspaceId)))
                .thenReturn(Optional.of(buildTransaction(txn1Id, UUID.randomUUID())));

        assertThatThrownBy(() -> transactionGroupService.createTransactionGroup(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already in a group");
    }

    @Test
    void createTransactionGroup_transactionNotFound_throws() {
        CreateTransactionGroupDto dto = CreateTransactionGroupDto.builder()
                .name("Trip")
                .transactionIds(Set.of(txn1Id, txn2Id))
                .build();

        when(transactionRepository.findByIdAndWorkspaceId(any(UUID.class), eq(workspaceId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionGroupService.createTransactionGroup(workspaceId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Transaction not found");
    }

    @Test
    void createTransactionGroup_noCategoryOrTags() {
        CreateTransactionGroupDto dto = CreateTransactionGroupDto.builder()
                .name("Simple Group")
                .transactionIds(Set.of(txn1Id, txn2Id))
                .build();

        when(transactionRepository.findByIdAndWorkspaceId(txn1Id, workspaceId))
                .thenReturn(Optional.of(buildTransaction(txn1Id, null)));
        when(transactionRepository.findByIdAndWorkspaceId(txn2Id, workspaceId))
                .thenReturn(Optional.of(buildTransaction(txn2Id, null)));
        when(transactionGroupRepository.save(any(TransactionGroup.class)))
                .thenAnswer(inv -> {
                    TransactionGroup g = inv.getArgument(0);
                    g.setId(groupId);
                    return g;
                });
        when(transactionRepository.setGroupId(eq(groupId), eq(Set.of(txn1Id, txn2Id)), eq(workspaceId)))
                .thenReturn(2);

        TransactionGroup result = transactionGroupService.createTransactionGroup(workspaceId, dto);

        assertThat(result.getCategoryId()).isNull();
        assertThat(result.getTagIds()).isEmpty();
    }

    // --- updateTransactionGroup ---

    @Test
    void updateTransactionGroup_updateName() {
        UpdateTransactionGroupDto dto = new UpdateTransactionGroupDto();
        // Use reflection-free approach: just set name
        UpdateTransactionGroupDto nameDto = UpdateTransactionGroupDto.builder().name("New Name").build();

        when(transactionGroupRepository.findByIdAndWorkspaceId(groupId, workspaceId))
                .thenReturn(Optional.of(buildGroup()));
        when(transactionGroupRepository.save(any(TransactionGroup.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransactionGroup result = transactionGroupService.updateTransactionGroup(groupId, workspaceId, nameDto);

        assertThat(result.getName()).isEqualTo("New Name");
    }

    @Test
    void updateTransactionGroup_setCategoryId() {
        UpdateTransactionGroupDto dto = new UpdateTransactionGroupDto();
        dto.assignCategoryId(categoryId);

        when(transactionGroupRepository.findByIdAndWorkspaceId(groupId, workspaceId))
                .thenReturn(Optional.of(buildGroup()));
        when(categoryService.getCategory(categoryId, workspaceId))
                .thenReturn(Category.builder().id(categoryId).build());
        when(transactionGroupRepository.save(any(TransactionGroup.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransactionGroup result = transactionGroupService.updateTransactionGroup(groupId, workspaceId, dto);

        assertThat(result.getCategoryId()).isEqualTo(categoryId);
    }

    @Test
    void updateTransactionGroup_clearCategoryId() {
        TransactionGroup group = buildGroup();
        group.setCategoryId(categoryId);

        UpdateTransactionGroupDto dto = new UpdateTransactionGroupDto();
        dto.assignCategoryId(null);

        when(transactionGroupRepository.findByIdAndWorkspaceId(groupId, workspaceId))
                .thenReturn(Optional.of(group));
        when(transactionGroupRepository.save(any(TransactionGroup.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransactionGroup result = transactionGroupService.updateTransactionGroup(groupId, workspaceId, dto);

        assertThat(result.getCategoryId()).isNull();
    }

    @Test
    void updateTransactionGroup_setTags() {
        UpdateTransactionGroupDto dto = new UpdateTransactionGroupDto();
        dto.assignTagIds(Set.of(tagId));

        when(transactionGroupRepository.findByIdAndWorkspaceId(groupId, workspaceId))
                .thenReturn(Optional.of(buildGroup()));
        when(tagService.getTag(tagId, workspaceId))
                .thenReturn(Tag.builder().id(tagId).build());
        when(transactionGroupRepository.save(any(TransactionGroup.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransactionGroup result = transactionGroupService.updateTransactionGroup(groupId, workspaceId, dto);

        assertThat(result.getTagIds()).containsExactly(tagId);
    }

    // --- updateTransactionGroup: transactionIds reconciliation ---

    @Test
    void updateTransactionGroup_addTransaction_viaTransactionIds() {
        TransactionGroup group = buildGroup();
        Transaction existingTxn1 = buildTransaction(txn1Id, groupId);
        Transaction existingTxn2 = buildTransaction(txn2Id, groupId);
        Transaction newTxn = buildTransaction(txn3Id, null);

        when(transactionGroupRepository.findByIdAndWorkspaceId(groupId, workspaceId))
                .thenReturn(Optional.of(group));
        when(transactionGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.findAllByGroupIdAndWorkspaceId(groupId, workspaceId))
                .thenReturn(List.of(existingTxn1, existingTxn2))
                .thenReturn(List.of(existingTxn1, existingTxn2, newTxn));
        when(transactionRepository.findByIdAndWorkspaceId(txn3Id, workspaceId))
                .thenReturn(Optional.of(newTxn));
        when(transactionRepository.setGroupId(groupId, Set.of(txn3Id), workspaceId)).thenReturn(1);

        UpdateTransactionGroupDto dto = UpdateTransactionGroupDto.builder()
                .transactionIds(Set.of(txn1Id, txn2Id, txn3Id))
                .build();

        transactionGroupService.updateTransactionGroup(groupId, workspaceId, dto);

        verify(transactionRepository).setGroupId(groupId, Set.of(txn3Id), workspaceId);
    }

    @Test
    void updateTransactionGroup_removeTransaction_viaTransactionIds() {
        TransactionGroup group = buildGroup();
        Transaction txn1 = buildTransaction(txn1Id, groupId);
        Transaction txn2 = buildTransaction(txn2Id, groupId);
        Transaction txn3 = buildTransaction(txn3Id, groupId);

        when(transactionGroupRepository.findByIdAndWorkspaceId(groupId, workspaceId))
                .thenReturn(Optional.of(group));
        when(transactionGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.findAllByGroupIdAndWorkspaceId(groupId, workspaceId))
                .thenReturn(List.of(txn1, txn2, txn3))
                .thenReturn(List.of(txn1, txn2));
        when(transactionRepository.findByIdAndWorkspaceId(txn3Id, workspaceId))
                .thenReturn(Optional.of(txn3));

        UpdateTransactionGroupDto dto = UpdateTransactionGroupDto.builder()
                .transactionIds(Set.of(txn1Id, txn2Id))
                .build();

        transactionGroupService.updateTransactionGroup(groupId, workspaceId, dto);

        verify(transactionRepository).save(txn3);
        assertThat(txn3.getGroupId()).isNull();
    }

    @Test
    void updateTransactionGroup_transactionIdsFewerThan2_throws() {
        TransactionGroup group = buildGroup();
        when(transactionGroupRepository.findByIdAndWorkspaceId(groupId, workspaceId))
                .thenReturn(Optional.of(group));
        when(transactionGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateTransactionGroupDto dto = UpdateTransactionGroupDto.builder()
                .transactionIds(Set.of(txn1Id))
                .build();

        assertThatThrownBy(() -> transactionGroupService.updateTransactionGroup(groupId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least 2");
    }

    // --- deleteTransactionGroup ---

    @Test
    void deleteTransactionGroup_dissolvesGroup() {
        when(transactionGroupRepository.findByIdAndWorkspaceId(groupId, workspaceId))
                .thenReturn(Optional.of(buildGroup()));
        when(transactionRepository.clearGroupId(groupId)).thenReturn(2);

        transactionGroupService.deleteTransactionGroup(groupId, workspaceId);

        verify(transactionRepository).clearGroupId(groupId);
        verify(transactionGroupRepository).delete(any(TransactionGroup.class));
    }

    // --- Mutual exclusivity: reject recurring-linked transactions ---

    @Test
    void createTransactionGroup_recurringLinkedTransaction_throws() {
        CreateTransactionGroupDto dto = CreateTransactionGroupDto.builder()
                .name("Trip")
                .transactionIds(Set.of(txn1Id, txn2Id))
                .build();

        Transaction txn = buildTransaction(txn1Id, null);
        txn.setRecurringItemId(UUID.randomUUID());
        when(transactionRepository.findByIdAndWorkspaceId(any(UUID.class), eq(workspaceId)))
                .thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> transactionGroupService.createTransactionGroup(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("recurring item")
                .hasMessageContaining("Unlink it");
    }

    @Test
    void updateTransactionGroup_addRecurringLinkedTransaction_throws() {
        TransactionGroup group = buildGroup();
        Transaction existingTxn1 = buildTransaction(txn1Id, groupId);
        Transaction existingTxn2 = buildTransaction(txn2Id, groupId);

        when(transactionGroupRepository.findByIdAndWorkspaceId(groupId, workspaceId))
                .thenReturn(Optional.of(group));
        when(transactionGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.findAllByGroupIdAndWorkspaceId(groupId, workspaceId))
                .thenReturn(List.of(existingTxn1, existingTxn2));

        Transaction recurringTxn = buildTransaction(txn3Id, null);
        recurringTxn.setRecurringItemId(UUID.randomUUID());
        when(transactionRepository.findByIdAndWorkspaceId(txn3Id, workspaceId))
                .thenReturn(Optional.of(recurringTxn));

        UpdateTransactionGroupDto dto = UpdateTransactionGroupDto.builder()
                .transactionIds(Set.of(txn1Id, txn2Id, txn3Id))
                .build();

        assertThatThrownBy(() -> transactionGroupService.updateTransactionGroup(groupId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("recurring item")
                .hasMessageContaining("Unlink it");
    }

    // --- Category Polarity Validation ---

    @Test
    void createTransactionGroup_polarityMismatch_throws() {
        Transaction txn1 = Transaction.builder().id(txn1Id).workspaceId(workspaceId).amount(new BigDecimal("50.00")).build();
        Transaction txn2 = Transaction.builder().id(txn2Id).workspaceId(workspaceId).amount(new BigDecimal("30.00")).build();

        CreateTransactionGroupDto dto = CreateTransactionGroupDto.builder()
                .name("Mixed Group")
                .categoryId(categoryId)
                .transactionIds(Set.of(txn1Id, txn2Id))
                .build();

        Category cat = Category.builder().id(categoryId).workspaceId(workspaceId).name("Groceries").income(false).build();
        when(categoryService.getCategory(categoryId, workspaceId)).thenReturn(cat);
        when(transactionRepository.findByIdAndWorkspaceId(eq(txn1Id), eq(workspaceId))).thenReturn(Optional.of(txn1));
        when(transactionRepository.findByIdAndWorkspaceId(eq(txn2Id), eq(workspaceId))).thenReturn(Optional.of(txn2));
        doThrow(new BadRequestException("Positive amounts must use an income category"))
                .when(categoryService).validateCategoryPolarity(eq(categoryId), any(BigDecimal.class), eq(workspaceId));

        assertThatThrownBy(() -> transactionGroupService.createTransactionGroup(workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("income category");
    }

    @Test
    void updateTransactionGroup_changeCategoryPolarityMismatch_throws() {
        UUID newCatId = UUID.randomUUID();
        TransactionGroup group = buildGroup();
        group.setCategoryId(categoryId);

        Transaction txn1 = Transaction.builder().id(txn1Id).workspaceId(workspaceId).amount(new BigDecimal("50.00")).build();
        Transaction txn2 = Transaction.builder().id(txn2Id).workspaceId(workspaceId).amount(new BigDecimal("30.00")).build();

        UpdateTransactionGroupDto dto = new UpdateTransactionGroupDto();
        dto.assignCategoryId(newCatId);

        when(transactionGroupRepository.findByIdAndWorkspaceId(groupId, workspaceId)).thenReturn(Optional.of(group));
        when(categoryService.getCategory(newCatId, workspaceId)).thenReturn(
                Category.builder().id(newCatId).workspaceId(workspaceId).name("Groceries").income(false).build());
        when(transactionGroupRepository.save(any())).thenReturn(group);
        when(transactionRepository.findAllByGroupIdAndWorkspaceId(groupId, workspaceId)).thenReturn(List.of(txn1, txn2));
        doThrow(new BadRequestException("Positive amounts must use an income category"))
                .when(categoryService).validateCategoryPolarity(eq(newCatId), any(BigDecimal.class), eq(workspaceId));

        assertThatThrownBy(() -> transactionGroupService.updateTransactionGroup(groupId, workspaceId, dto))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("income category");
    }
}
