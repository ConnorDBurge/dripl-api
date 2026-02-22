package com.dripl.budget;

import com.dripl.account.entity.Account;
import com.dripl.account.repository.AccountRepository;
import com.dripl.budget.dto.CreateBudgetDto;
import com.dripl.budget.dto.UpdateBudgetDto;
import com.dripl.budget.entity.Budget;
import com.dripl.budget.repository.BudgetAccountRepository;
import com.dripl.budget.repository.BudgetRepository;
import com.dripl.budget.service.BudgetService;
import com.dripl.common.exception.BadRequestException;
import com.dripl.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private BudgetAccountRepository budgetAccountRepository;
    @Mock private AccountRepository accountRepository;
    @InjectMocks private BudgetService service;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();

    @Nested
    class CreateBudget {

        @Test
        void create_monthlyBudget_succeeds() {
            CreateBudgetDto dto = CreateBudgetDto.builder()
                    .name("Monthly Bills")
                    .anchorDay1(1)
                    .build();
            Budget saved = Budget.builder()
                    .id(budgetId).workspaceId(workspaceId).name("Monthly Bills").anchorDay1(1).build();

            when(budgetRepository.existsByWorkspaceIdAndName(workspaceId, "Monthly Bills")).thenReturn(false);
            when(budgetRepository.save(any(Budget.class))).thenReturn(saved);

            Budget result = service.createBudget(workspaceId, dto);

            assertThat(result.getName()).isEqualTo("Monthly Bills");
            assertThat(result.getAnchorDay1()).isEqualTo(1);
        }

        @Test
        void create_fixedInterval_succeeds() {
            LocalDate anchor = LocalDate.now().minusDays(7);
            CreateBudgetDto dto = CreateBudgetDto.builder()
                    .name("Biweekly")
                    .intervalDays(14)
                    .anchorDate(anchor)
                    .build();
            Budget saved = Budget.builder()
                    .id(budgetId).workspaceId(workspaceId).name("Biweekly")
                    .intervalDays(14).anchorDate(anchor).build();

            when(budgetRepository.existsByWorkspaceIdAndName(workspaceId, "Biweekly")).thenReturn(false);
            when(budgetRepository.save(any(Budget.class))).thenReturn(saved);

            Budget result = service.createBudget(workspaceId, dto);

            assertThat(result.getIntervalDays()).isEqualTo(14);
        }

        @Test
        void create_withAccounts_savesLinks() {
            UUID acc1 = UUID.randomUUID();
            UUID acc2 = UUID.randomUUID();
            CreateBudgetDto dto = CreateBudgetDto.builder()
                    .name("Test")
                    .anchorDay1(1)
                    .accountIds(List.of(acc1, acc2))
                    .build();
            Budget saved = Budget.builder()
                    .id(budgetId).workspaceId(workspaceId).name("Test").anchorDay1(1).build();

            when(budgetRepository.existsByWorkspaceIdAndName(workspaceId, "Test")).thenReturn(false);
            when(budgetRepository.save(any(Budget.class))).thenReturn(saved);
            when(accountRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(
                    Account.builder().id(acc1).workspaceId(workspaceId).build(),
                    Account.builder().id(acc2).workspaceId(workspaceId).build()));

            Budget result = service.createBudget(workspaceId, dto);

            verify(budgetAccountRepository).saveAll(anyList());
            assertThat(result.getAccountIds()).containsExactly(acc1, acc2);
        }

        @Test
        void create_withInvalidAccountIds_throws() {
            UUID validAcc = UUID.randomUUID();
            UUID invalidAcc = UUID.randomUUID();
            CreateBudgetDto dto = CreateBudgetDto.builder()
                    .name("Test")
                    .anchorDay1(1)
                    .accountIds(List.of(validAcc, invalidAcc))
                    .build();
            Budget saved = Budget.builder()
                    .id(budgetId).workspaceId(workspaceId).name("Test").anchorDay1(1).build();

            when(budgetRepository.existsByWorkspaceIdAndName(workspaceId, "Test")).thenReturn(false);
            when(budgetRepository.save(any(Budget.class))).thenReturn(saved);
            when(accountRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(
                    Account.builder().id(validAcc).workspaceId(workspaceId).build()));

            assertThatThrownBy(() -> service.createBudget(workspaceId, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not found in workspace");
        }

        @Test
        void create_duplicateName_throws() {
            CreateBudgetDto dto = CreateBudgetDto.builder()
                    .name("Existing")
                    .anchorDay1(1)
                    .build();
            when(budgetRepository.existsByWorkspaceIdAndName(workspaceId, "Existing")).thenReturn(true);

            assertThatThrownBy(() -> service.createBudget(workspaceId, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        void create_noPeriodConfig_throws() {
            CreateBudgetDto dto = CreateBudgetDto.builder().name("Bad").build();
            when(budgetRepository.existsByWorkspaceIdAndName(workspaceId, "Bad")).thenReturn(false);

            assertThatThrownBy(() -> service.createBudget(workspaceId, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("period configuration");
        }

        @Test
        void create_mixedModes_throws() {
            CreateBudgetDto dto = CreateBudgetDto.builder()
                    .name("Mixed")
                    .anchorDay1(1)
                    .intervalDays(14)
                    .anchorDate(LocalDate.now())
                    .build();
            when(budgetRepository.existsByWorkspaceIdAndName(workspaceId, "Mixed")).thenReturn(false);

            assertThatThrownBy(() -> service.createBudget(workspaceId, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Cannot mix");
        }

        @Test
        void create_anchorDay1OutOfRange_throws() {
            CreateBudgetDto dto = CreateBudgetDto.builder()
                    .name("Bad Day")
                    .anchorDay1(32)
                    .build();
            when(budgetRepository.existsByWorkspaceIdAndName(workspaceId, "Bad Day")).thenReturn(false);

            assertThatThrownBy(() -> service.createBudget(workspaceId, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("anchorDay1 must be between 1 and 31");
        }

        @Test
        void create_anchorDay2SameAsDay1_throws() {
            CreateBudgetDto dto = CreateBudgetDto.builder()
                    .name("Same Days")
                    .anchorDay1(15)
                    .anchorDay2(15)
                    .build();
            when(budgetRepository.existsByWorkspaceIdAndName(workspaceId, "Same Days")).thenReturn(false);

            assertThatThrownBy(() -> service.createBudget(workspaceId, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("must be different");
        }

        @Test
        void create_intervalDaysZero_throws() {
            CreateBudgetDto dto = CreateBudgetDto.builder()
                    .name("Zero")
                    .intervalDays(0)
                    .anchorDate(LocalDate.now())
                    .build();
            when(budgetRepository.existsByWorkspaceIdAndName(workspaceId, "Zero")).thenReturn(false);

            assertThatThrownBy(() -> service.createBudget(workspaceId, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("intervalDays must be at least 1");
        }
    }

    @Nested
    class ListBudgets {

        @Test
        void list_returnsBudgets() {
            Budget b1 = Budget.builder().id(UUID.randomUUID()).workspaceId(workspaceId).name("A").anchorDay1(1).build();
            Budget b2 = Budget.builder().id(UUID.randomUUID()).workspaceId(workspaceId).name("B").anchorDay1(15).build();
            when(budgetRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(b1, b2));
            when(budgetAccountRepository.findAllByBudgetIdIn(List.of(b1.getId(), b2.getId()))).thenReturn(List.of());

            List<Budget> result = service.listBudgets(workspaceId);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class GetBudget {

        @Test
        void get_found_returnsBudget() {
            Budget budget = Budget.builder()
                    .id(budgetId).workspaceId(workspaceId).name("Test").anchorDay1(1).build();
            when(budgetRepository.findByIdAndWorkspaceId(budgetId, workspaceId)).thenReturn(Optional.of(budget));
            when(budgetAccountRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            Budget result = service.getBudget(workspaceId, budgetId);

            assertThat(result.getId()).isEqualTo(budgetId);
            assertThat(result.getName()).isEqualTo("Test");
            assertThat(result.getAccountIds()).isEmpty();
        }

        @Test
        void get_notFound_throws() {
            when(budgetRepository.findByIdAndWorkspaceId(budgetId, workspaceId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getBudget(workspaceId, budgetId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Budget not found");
        }
    }

    @Nested
    class UpdateBudget {

        @Test
        void update_name_succeeds() {
            Budget existing = Budget.builder()
                    .id(budgetId).workspaceId(workspaceId).name("Old").anchorDay1(1).build();
            when(budgetRepository.findByIdAndWorkspaceId(budgetId, workspaceId)).thenReturn(Optional.of(existing));
            when(budgetRepository.existsByWorkspaceIdAndName(workspaceId, "New")).thenReturn(false);
            when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> inv.getArgument(0));
            when(budgetAccountRepository.findAllByBudgetId(budgetId)).thenReturn(List.of());

            UpdateBudgetDto dto = UpdateBudgetDto.builder().name("New").build();
            Budget result = service.updateBudget(workspaceId, budgetId, dto);

            assertThat(result.getName()).isEqualTo("New");
            assertThat(result.getAccountIds()).isEmpty();
        }

        @Test
        void update_duplicateName_throws() {
            Budget existing = Budget.builder()
                    .id(budgetId).workspaceId(workspaceId).name("Old").anchorDay1(1).build();
            when(budgetRepository.findByIdAndWorkspaceId(budgetId, workspaceId)).thenReturn(Optional.of(existing));
            when(budgetRepository.existsByWorkspaceIdAndName(workspaceId, "Taken")).thenReturn(true);

            UpdateBudgetDto dto = UpdateBudgetDto.builder().name("Taken").build();

            assertThatThrownBy(() -> service.updateBudget(workspaceId, budgetId, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        void update_accountIds_replacesLinks() {
            UUID newAcc = UUID.randomUUID();
            Budget existing = Budget.builder()
                    .id(budgetId).workspaceId(workspaceId).name("Test").anchorDay1(1).build();
            when(budgetRepository.findByIdAndWorkspaceId(budgetId, workspaceId)).thenReturn(Optional.of(existing));
            when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> inv.getArgument(0));
            when(accountRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of(
                    Account.builder().id(newAcc).workspaceId(workspaceId).build()));

            UpdateBudgetDto dto = UpdateBudgetDto.builder().accountIds(List.of(newAcc)).build();
            Budget result = service.updateBudget(workspaceId, budgetId, dto);

            verify(budgetAccountRepository).deleteAllByBudgetId(budgetId);
            verify(budgetAccountRepository).saveAll(anyList());
            assertThat(result.getAccountIds()).containsExactly(newAcc);
        }

        @Test
        void update_invalidAccountIds_throws() {
            UUID invalidAcc = UUID.randomUUID();
            Budget existing = Budget.builder()
                    .id(budgetId).workspaceId(workspaceId).name("Test").anchorDay1(1).build();
            when(budgetRepository.findByIdAndWorkspaceId(budgetId, workspaceId)).thenReturn(Optional.of(existing));
            when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> inv.getArgument(0));
            when(accountRepository.findAllByWorkspaceId(workspaceId)).thenReturn(List.of());

            UpdateBudgetDto dto = UpdateBudgetDto.builder().accountIds(List.of(invalidAcc)).build();

            assertThatThrownBy(() -> service.updateBudget(workspaceId, budgetId, dto))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not found in workspace");
        }
    }

    @Nested
    class DeleteBudget {

        @Test
        void delete_found_succeeds() {
            Budget budget = Budget.builder()
                    .id(budgetId).workspaceId(workspaceId).name("Delete Me").anchorDay1(1).build();
            when(budgetRepository.findByIdAndWorkspaceId(budgetId, workspaceId)).thenReturn(Optional.of(budget));

            service.deleteBudget(workspaceId, budgetId);

            verify(budgetRepository).delete(budget);
        }

        @Test
        void delete_notFound_throws() {
            when(budgetRepository.findByIdAndWorkspaceId(budgetId, workspaceId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteBudget(workspaceId, budgetId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
