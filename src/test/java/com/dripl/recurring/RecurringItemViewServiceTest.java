package com.dripl.recurring;

import com.dripl.account.enums.CurrencyCode;
import com.dripl.common.exception.BadRequestException;
import com.dripl.recurring.dto.RecurringItemMonthViewDto;
import com.dripl.recurring.dto.RecurringItemViewDto;
import com.dripl.recurring.dto.RecurringOccurrenceDto;
import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.entity.RecurringItemOverride;
import com.dripl.recurring.enums.FrequencyGranularity;
import com.dripl.recurring.enums.RecurringItemStatus;
import com.dripl.recurring.repository.RecurringItemOverrideRepository;
import com.dripl.recurring.repository.RecurringItemRepository;
import com.dripl.recurring.service.RecurringItemViewService;
import com.dripl.transaction.entity.Transaction;
import com.dripl.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringItemViewServiceTest {

    @Mock
    private RecurringItemRepository recurringItemRepository;

    @Mock
    private RecurringItemOverrideRepository overrideRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private RecurringItemViewService viewService;

    private static final UUID WS = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(overrideRepository.findByWorkspaceIdAndOccurrenceDateBetween(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(transactionRepository.findLinkedToRecurringItemsInDateRange(any(), any(), any()))
                .thenReturn(List.of());
    }

    private RecurringItem buildItem(String desc, BigDecimal amount, FrequencyGranularity gran,
                                     int qty, LocalDate start, LocalDate end,
                                     RecurringItemStatus status, LocalDate... anchors) {
        return RecurringItem.builder()
                .id(UUID.randomUUID())
                .workspaceId(WS)
                .description(desc)
                .merchantId(UUID.randomUUID())
                .accountId(UUID.randomUUID())
                .categoryId(UUID.randomUUID())
                .amount(amount)
                .currencyCode(CurrencyCode.USD)
                .frequencyGranularity(gran)
                .frequencyQuantity(qty)
                .startDate(start.atStartOfDay())
                .endDate(end != null ? end.atStartOfDay() : null)
                .status(status)
                .anchorDates(List.of(anchors).stream().map(LocalDate::atStartOfDay).toList())
                .build();
    }

    @Test
    void getMonthView_happyPath_returnsItemsWithOccurrences() {
        RecurringItem monthly = buildItem("Rent", new BigDecimal("1500.00"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 15));

        RecurringItem biweekly = buildItem("Groceries", new BigDecimal("200.00"),
                FrequencyGranularity.WEEK, 2, LocalDate.of(2026, 1, 3), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 3));

        when(recurringItemRepository.findAllByWorkspaceId(WS))
                .thenReturn(List.of(monthly, biweekly));

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, YearMonth.of(2026, 3), null);

        assertThat(result.getMonthStart()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(result.getMonthEnd()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(result.getItemCount()).isEqualTo(2);
        assertThat(result.getItems()).hasSize(2);

        // Sorted by first occurrence: biweekly first (Mar ~early), then rent (Mar 15)
        RecurringItemViewDto first = result.getItems().get(0);
        assertThat(first.getOccurrences().get(0).getDate().getMonthValue()).isEqualTo(3);
        assertThat(first.getOccurrences().get(0).getExpectedAmount()).isNotNull();
        assertThat(first.getFrequencyGranularity()).isNotNull();
        assertThat(first.getFrequencyQuantity()).isNotNull();
        assertThat(first.getTotalExpected()).isNotNull();
    }

    @Test
    void getMonthView_inactiveItemsExcluded() {
        RecurringItem active = buildItem("Netflix", new BigDecimal("14.99"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 10));

        RecurringItem paused = buildItem("Gym", new BigDecimal("50.00"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.PAUSED, LocalDate.of(2026, 1, 5));

        RecurringItem cancelled = buildItem("Old Sub", new BigDecimal("9.99"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.CANCELLED, LocalDate.of(2026, 1, 20));

        when(recurringItemRepository.findAllByWorkspaceId(WS))
                .thenReturn(List.of(active, paused, cancelled));

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, YearMonth.of(2026, 3), null);

        assertThat(result.getItemCount()).isEqualTo(1);
        assertThat(result.getItems().get(0).getDescription()).isEqualTo("Netflix");
    }

    @Test
    void getMonthView_emptyMonth_noOccurrences() {
        RecurringItem yearly = buildItem("Insurance", new BigDecimal("600.00"),
                FrequencyGranularity.YEAR, 1, LocalDate.of(2025, 6, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2025, 6, 1));

        when(recurringItemRepository.findAllByWorkspaceId(WS)).thenReturn(List.of(yearly));

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, YearMonth.of(2026, 3), null);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getItemCount()).isZero();
        assertThat(result.getOccurrenceCount()).isZero();
        assertThat(result.getExpectedExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getExpectedIncome()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getMonthView_totalExpectedSumsCorrectly() {
        RecurringItem item1 = buildItem("A", new BigDecimal("100.00"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 10));

        RecurringItem item2 = buildItem("B", new BigDecimal("50.00"),
                FrequencyGranularity.WEEK, 2, LocalDate.of(2026, 1, 5), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 5));

        when(recurringItemRepository.findAllByWorkspaceId(WS)).thenReturn(List.of(item1, item2));

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, YearMonth.of(2026, 3), null);

        // Both items are positive → all income
        BigDecimal item1Total = result.getItems().stream()
                .filter(i -> i.getDescription().equals("A"))
                .map(RecurringItemViewDto::getTotalExpected)
                .findFirst().orElse(BigDecimal.ZERO);
        assertThat(item1Total).isEqualByComparingTo(new BigDecimal("100.00"));

        BigDecimal summedTotal = result.getItems().stream()
                .map(RecurringItemViewDto::getTotalExpected)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(result.getExpectedIncome()).isEqualByComparingTo(summedTotal);
        assertThat(result.getExpectedExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getMonthView_sortedByFirstOccurrence() {
        // Item with occurrence on 25th
        RecurringItem late = buildItem("Late", new BigDecimal("10.00"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 25));

        // Item with occurrence on 5th
        RecurringItem early = buildItem("Early", new BigDecimal("10.00"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 5));

        when(recurringItemRepository.findAllByWorkspaceId(WS)).thenReturn(List.of(late, early));

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, YearMonth.of(2026, 3), null);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getDescription()).isEqualTo("Early");
        assertThat(result.getItems().get(1).getDescription()).isEqualTo("Late");
    }

    @Test
    void getMonthView_endDateExcludesItem() {
        RecurringItem ended = buildItem("Ended", new BigDecimal("25.00"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 15),
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 10));

        when(recurringItemRepository.findAllByWorkspaceId(WS)).thenReturn(List.of(ended));

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, YearMonth.of(2026, 3), null);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void getMonthView_multipleAnchors_twiceAMonth() {
        RecurringItem bimonthly = buildItem("Payroll", new BigDecimal("3000.00"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 15));

        when(recurringItemRepository.findAllByWorkspaceId(WS)).thenReturn(List.of(bimonthly));

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, YearMonth.of(2026, 3), null);

        assertThat(result.getItems()).hasSize(1);
        RecurringItemViewDto item = result.getItems().get(0);
        assertThat(item.getOccurrences()).extracting(RecurringOccurrenceDto::getDate)
                .containsExactly(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15));
        assertThat(item.getOccurrences()).extracting(RecurringOccurrenceDto::getExpectedAmount)
                .containsExactly(new BigDecimal("3000.00"), new BigDecimal("3000.00"));
        assertThat(item.getTotalExpected()).isEqualByComparingTo(new BigDecimal("6000.00"));
        assertThat(result.getOccurrenceCount()).isEqualTo(2);
    }

    @Test
    void getMonthView_noItemsInWorkspace() {
        when(recurringItemRepository.findAllByWorkspaceId(WS)).thenReturn(List.of());

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, YearMonth.of(2026, 3), null);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getItemCount()).isZero();
        assertThat(result.getOccurrenceCount()).isZero();
        assertThat(result.getExpectedExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getExpectedIncome()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getMonthView_defaultsToCurrentMonth() {
        when(recurringItemRepository.findAllByWorkspaceId(WS)).thenReturn(List.of());

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, null, null);

        YearMonth now = YearMonth.now();
        assertThat(result.getMonthStart()).isEqualTo(now.atDay(1));
        assertThat(result.getMonthEnd()).isEqualTo(now.atEndOfMonth());
    }

    @Test
    void getMonthView_periodOffsetResolvesRelativeMonth() {
        when(recurringItemRepository.findAllByWorkspaceId(WS)).thenReturn(List.of());

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, null, -2);

        YearMonth expected = YearMonth.now().minusMonths(2);
        assertThat(result.getMonthStart()).isEqualTo(expected.atDay(1));
        assertThat(result.getMonthEnd()).isEqualTo(expected.atEndOfMonth());
    }

    @Test
    void getMonthView_bothParamsThrows() {
        assertThatThrownBy(() -> viewService.getMonthView(WS, YearMonth.of(2026, 3), 1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("both");
    }

    @Test
    void getMonthView_splitsExpensesAndIncome() {
        RecurringItem expense = buildItem("Rent", new BigDecimal("-1500.00"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 1));

        RecurringItem income = buildItem("Salary", new BigDecimal("5000.00"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 15));

        when(recurringItemRepository.findAllByWorkspaceId(WS)).thenReturn(List.of(expense, income));
        when(overrideRepository.findByWorkspaceIdAndOccurrenceDateBetween(any(), any(), any()))
                .thenReturn(List.of());

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, YearMonth.of(2026, 3), null);

        assertThat(result.getExpectedExpenses()).isEqualByComparingTo(new BigDecimal("-1500.00"));
        assertThat(result.getExpectedIncome()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(result.getItemCount()).isEqualTo(2);
    }

    @Test
    void getMonthView_overrideAppliedToOccurrence() {
        RecurringItem rent = buildItem("Rent", new BigDecimal("-1500.00"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 10));

        when(recurringItemRepository.findAllByWorkspaceId(WS)).thenReturn(List.of(rent));

        RecurringItemOverride override = RecurringItemOverride.builder()
                .id(UUID.randomUUID())
                .recurringItemId(rent.getId())
                .occurrenceDate(LocalDate.of(2026, 3, 10))
                .amount(new BigDecimal("-1700.00"))
                .notes("Rent increase")
                .build();
        when(overrideRepository.findByWorkspaceIdAndOccurrenceDateBetween(WS,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(List.of(override));

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, YearMonth.of(2026, 3), null);

        RecurringOccurrenceDto occ = result.getItems().get(0).getOccurrences().get(0);
        assertThat(occ.getExpectedAmount()).isEqualByComparingTo("-1700.00");
        assertThat(occ.getOverrideId()).isNotNull();
        assertThat(occ.getNotes()).isEqualTo("Rent increase");
        assertThat(result.getItems().get(0).getTotalExpected()).isEqualByComparingTo("-1700.00");
        assertThat(result.getExpectedExpenses()).isEqualByComparingTo("-1700.00");
    }

    @Test
    void getMonthView_overrideAffectsTotals() {
        // Two monthly items, one has an override
        RecurringItem a = buildItem("A", new BigDecimal("-100.00"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 5));

        RecurringItem b = buildItem("B", new BigDecimal("-200.00"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 15));

        when(recurringItemRepository.findAllByWorkspaceId(WS)).thenReturn(List.of(a, b));

        RecurringItemOverride override = RecurringItemOverride.builder()
                .id(UUID.randomUUID())
                .recurringItemId(a.getId())
                .occurrenceDate(LocalDate.of(2026, 3, 5))
                .amount(new BigDecimal("-150.00"))
                .build();
        when(overrideRepository.findByWorkspaceIdAndOccurrenceDateBetween(any(), any(), any()))
                .thenReturn(List.of(override));

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, YearMonth.of(2026, 3), null);

        // A uses override: -150, B uses default: -200 → total expenses = -350
        assertThat(result.getExpectedExpenses()).isEqualByComparingTo("-350.00");
    }

    @Test
    void getMonthView_nonOverriddenOccurrenceShowsFalseFlag() {
        RecurringItem item = buildItem("Sub", new BigDecimal("-10.00"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 20));

        when(recurringItemRepository.findAllByWorkspaceId(WS)).thenReturn(List.of(item));
        when(overrideRepository.findByWorkspaceIdAndOccurrenceDateBetween(any(), any(), any()))
                .thenReturn(List.of());

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, YearMonth.of(2026, 3), null);

        RecurringOccurrenceDto occ = result.getItems().get(0).getOccurrences().get(0);
        assertThat(occ.getOverrideId()).isNull();
        assertThat(occ.getTransaction()).isNull();
        assertThat(occ.getNotes()).isNull();
        assertThat(occ.getExpectedAmount()).isEqualByComparingTo("-10.00");
    }

    @Test
    void getMonthView_paidOccurrenceUsesTransactionAmount() {
        RecurringItem rent = buildItem("Rent", new BigDecimal("-1500.00"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 10));

        UUID txnId = UUID.randomUUID();
        Transaction txn = Transaction.builder()
                .id(txnId)
                .workspaceId(WS)
                .recurringItemId(rent.getId())
                .date(LocalDate.of(2026, 3, 10).atStartOfDay())
                .amount(new BigDecimal("-1550.00"))
                .build();

        when(recurringItemRepository.findAllByWorkspaceId(WS)).thenReturn(List.of(rent));
        when(transactionRepository.findLinkedToRecurringItemsInDateRange(any(), any(), any()))
                .thenReturn(List.of(txn));

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, YearMonth.of(2026, 3), null);

        RecurringOccurrenceDto occ = result.getItems().get(0).getOccurrences().get(0);
        assertThat(occ.getTransaction()).isNotNull();
        assertThat(occ.getTransaction().getId()).isEqualTo(txnId);
        assertThat(occ.getTransaction().getAmount()).isEqualByComparingTo("-1550.00");
        assertThat(occ.getExpectedAmount()).isEqualByComparingTo("-1500.00");
        assertThat(result.getExpectedExpenses()).isEqualByComparingTo("-1500.00");
    }

    @Test
    void getMonthView_paidTrumpsOverride() {
        RecurringItem item = buildItem("Electric", new BigDecimal("-120.00"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 5));

        RecurringItemOverride override = RecurringItemOverride.builder()
                .id(UUID.randomUUID())
                .recurringItemId(item.getId())
                .occurrenceDate(LocalDate.of(2026, 3, 5))
                .amount(new BigDecimal("-150.00"))
                .notes("Expected higher")
                .build();

        UUID txnId = UUID.randomUUID();
        Transaction txn = Transaction.builder()
                .id(txnId)
                .workspaceId(WS)
                .recurringItemId(item.getId())
                .date(LocalDate.of(2026, 3, 5).atStartOfDay())
                .amount(new BigDecimal("-145.00"))
                .build();

        when(recurringItemRepository.findAllByWorkspaceId(WS)).thenReturn(List.of(item));
        when(overrideRepository.findByWorkspaceIdAndOccurrenceDateBetween(any(), any(), any()))
                .thenReturn(List.of(override));
        when(transactionRepository.findLinkedToRecurringItemsInDateRange(any(), any(), any()))
                .thenReturn(List.of(txn));

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, YearMonth.of(2026, 3), null);

        RecurringOccurrenceDto occ = result.getItems().get(0).getOccurrences().get(0);
        assertThat(occ.getTransaction()).isNotNull();
        assertThat(occ.getOverrideId()).isNotNull();
        assertThat(occ.getExpectedAmount()).isEqualByComparingTo("-150.00"); // override amount
        assertThat(occ.getTransaction().getId()).isEqualTo(txnId);
        assertThat(occ.getTransaction().getAmount()).isEqualByComparingTo("-145.00");
    }

    @Test
    void getMonthView_unpaidOccurrenceShowsFalse() {
        RecurringItem item = buildItem("Netflix", new BigDecimal("-14.99"),
                FrequencyGranularity.MONTH, 1, LocalDate.of(2026, 1, 1), null,
                RecurringItemStatus.ACTIVE, LocalDate.of(2026, 1, 15));

        when(recurringItemRepository.findAllByWorkspaceId(WS)).thenReturn(List.of(item));

        RecurringItemMonthViewDto result = viewService.getMonthView(WS, YearMonth.of(2026, 3), null);

        RecurringOccurrenceDto occ = result.getItems().get(0).getOccurrences().get(0);
        assertThat(occ.getTransaction()).isNull();
        assertThat(occ.getExpectedAmount()).isEqualByComparingTo("-14.99");
    }

}
