package com.dripl.recurring;

import com.dripl.recurring.entity.RecurringItem;
import com.dripl.recurring.enums.FrequencyGranularity;
import com.dripl.recurring.enums.RecurringItemStatus;
import com.dripl.recurring.util.RecurringOccurrenceCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecurringOccurrenceCalculatorTest {

    private RecurringItem buildItem(FrequencyGranularity granularity, int quantity,
                                     LocalDate startDate, LocalDate endDate, LocalDate... anchors) {
        List<LocalDateTime> anchorDates = List.of(anchors).stream()
                .map(d -> d.atStartOfDay())
                .toList();
        return RecurringItem.builder()
                .id(UUID.randomUUID())
                .workspaceId(UUID.randomUUID())
                .amount(BigDecimal.TEN)
                .frequencyGranularity(granularity)
                .frequencyQuantity(quantity)
                .startDate(startDate.atStartOfDay())
                .endDate(endDate != null ? endDate.atStartOfDay() : null)
                .anchorDates(anchorDates)
                .status(RecurringItemStatus.ACTIVE)
                .build();
    }

    @Test
    void monthlyItem_oneOccurrenceInMonth() {
        RecurringItem ri = buildItem(FrequencyGranularity.MONTH, 1,
                LocalDate.of(2026, 1, 15), null, LocalDate.of(2026, 1, 15));

        List<LocalDate> dates = RecurringOccurrenceCalculator.computeOccurrences(ri,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(dates).containsExactly(LocalDate.of(2026, 3, 15));
    }

    @Test
    void biweeklyItem_twoOccurrencesInMonth() {
        RecurringItem ri = buildItem(FrequencyGranularity.WEEK, 2,
                LocalDate.of(2026, 1, 2), null, LocalDate.of(2026, 1, 2));

        List<LocalDate> dates = RecurringOccurrenceCalculator.computeOccurrences(ri,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));

        // Jan 2 + 2 weeks each: Jan 16, Jan 30, Feb 13, Feb 27, Mar 13...
        assertThat(dates).containsExactly(LocalDate.of(2026, 2, 13), LocalDate.of(2026, 2, 27));
    }

    @Test
    void weeklyItem_multipleOccurrences() {
        RecurringItem ri = buildItem(FrequencyGranularity.WEEK, 1,
                LocalDate.of(2026, 3, 2), null, LocalDate.of(2026, 3, 2));

        List<LocalDate> dates = RecurringOccurrenceCalculator.computeOccurrences(ri,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        // Mar 2, 9, 16, 23, 30
        assertThat(dates).hasSize(5);
        assertThat(dates.get(0)).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(dates.get(4)).isEqualTo(LocalDate.of(2026, 3, 30));
    }

    @Test
    void yearlyItem_noOccurrenceInWrongMonth() {
        RecurringItem ri = buildItem(FrequencyGranularity.YEAR, 1,
                LocalDate.of(2025, 6, 15), null, LocalDate.of(2025, 6, 15));

        List<LocalDate> dates = RecurringOccurrenceCalculator.computeOccurrences(ri,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(dates).isEmpty();
    }

    @Test
    void yearlyItem_occurrenceInCorrectMonth() {
        RecurringItem ri = buildItem(FrequencyGranularity.YEAR, 1,
                LocalDate.of(2025, 3, 10), null, LocalDate.of(2025, 3, 10));

        List<LocalDate> dates = RecurringOccurrenceCalculator.computeOccurrences(ri,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(dates).containsExactly(LocalDate.of(2026, 3, 10));
    }

    @Test
    void endDateBefore_noOccurrences() {
        RecurringItem ri = buildItem(FrequencyGranularity.MONTH, 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 15), LocalDate.of(2026, 1, 1));

        List<LocalDate> dates = RecurringOccurrenceCalculator.computeOccurrences(ri,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(dates).isEmpty();
    }

    @Test
    void startDateAfter_noOccurrences() {
        RecurringItem ri = buildItem(FrequencyGranularity.MONTH, 1,
                LocalDate.of(2026, 4, 1), null, LocalDate.of(2026, 4, 1));

        List<LocalDate> dates = RecurringOccurrenceCalculator.computeOccurrences(ri,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(dates).isEmpty();
    }

    @Test
    void dailyItem_everyThreeDays() {
        RecurringItem ri = buildItem(FrequencyGranularity.DAY, 3,
                LocalDate.of(2026, 3, 1), null, LocalDate.of(2026, 3, 1));

        List<LocalDate> dates = RecurringOccurrenceCalculator.computeOccurrences(ri,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10));

        // Mar 1, 4, 7, 10
        assertThat(dates).containsExactly(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 4),
                LocalDate.of(2026, 3, 7),
                LocalDate.of(2026, 3, 10));
    }

    @Test
    void countOccurrences_returnsSize() {
        RecurringItem ri = buildItem(FrequencyGranularity.MONTH, 1,
                LocalDate.of(2026, 1, 15), null, LocalDate.of(2026, 1, 15));

        int count = RecurringOccurrenceCalculator.countOccurrences(ri,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(count).isEqualTo(1);
    }

    @Test
    void multipleAnchorDates_allComputed() {
        RecurringItem ri = buildItem(FrequencyGranularity.MONTH, 1,
                LocalDate.of(2026, 1, 1), null,
                LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 20));

        List<LocalDate> dates = RecurringOccurrenceCalculator.computeOccurrences(ri,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        assertThat(dates).containsExactly(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 20));
    }

    @Test
    void resultsSortedByDate() {
        // Two anchors that produce interleaved dates
        RecurringItem ri = buildItem(FrequencyGranularity.WEEK, 2,
                LocalDate.of(2026, 1, 1), null,
                LocalDate.of(2026, 3, 6), LocalDate.of(2026, 3, 2));

        List<LocalDate> dates = RecurringOccurrenceCalculator.computeOccurrences(ri,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));

        // Should be sorted
        assertThat(dates).isSorted();
    }
}
