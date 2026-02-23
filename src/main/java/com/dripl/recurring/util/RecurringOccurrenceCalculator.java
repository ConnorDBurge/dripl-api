package com.dripl.recurring.util;

import com.dripl.recurring.entity.RecurringItem;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class RecurringOccurrenceCalculator {

    private RecurringOccurrenceCalculator() {}

    /**
     * Returns all occurrence dates for a recurring item within the given date range (inclusive).
     */
    public static List<LocalDate> computeOccurrences(RecurringItem ri, LocalDate rangeStart, LocalDate rangeEnd) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate riStart = ri.getStartDate().toLocalDate();
        LocalDate riEnd = ri.getEndDate() != null ? ri.getEndDate().toLocalDate() : null;

        for (LocalDateTime anchorDt : ri.getAnchorDates()) {
            LocalDate anchor = anchorDt.toLocalDate();
            LocalDate current = anchor;

            // Forward from anchor
            while (!current.isAfter(rangeEnd)) {
                if (!current.isBefore(rangeStart) && !current.isBefore(riStart)
                        && (riEnd == null || !current.isAfter(riEnd))) {
                    dates.add(current);
                }
                current = advanceByFrequency(current, ri);
            }

            // Backward from anchor if anchor is after range
            if (anchor.isAfter(rangeEnd)) {
                current = anchor;
                while (current.isAfter(rangeStart)) {
                    current = retreatByFrequency(current, ri);
                    if (!current.isBefore(rangeStart) && !current.isAfter(rangeEnd)
                            && !current.isBefore(riStart)
                            && (riEnd == null || !current.isAfter(riEnd))) {
                        dates.add(current);
                    }
                }
            }
        }

        dates.sort(LocalDate::compareTo);
        return dates;
    }

    /**
     * Returns the count of occurrences within the given date range.
     */
    public static int countOccurrences(RecurringItem ri, LocalDate rangeStart, LocalDate rangeEnd) {
        return computeOccurrences(ri, rangeStart, rangeEnd).size();
    }

    private static LocalDate advanceByFrequency(LocalDate date, RecurringItem ri) {
        int qty = ri.getFrequencyQuantity();
        return switch (ri.getFrequencyGranularity()) {
            case DAY -> date.plusDays(qty);
            case WEEK -> date.plusWeeks(qty);
            case MONTH -> date.plusMonths(qty);
            case YEAR -> date.plusYears(qty);
        };
    }

    private static LocalDate retreatByFrequency(LocalDate date, RecurringItem ri) {
        int qty = ri.getFrequencyQuantity();
        return switch (ri.getFrequencyGranularity()) {
            case DAY -> date.minusDays(qty);
            case WEEK -> date.minusWeeks(qty);
            case MONTH -> date.minusMonths(qty);
            case YEAR -> date.minusYears(qty);
        };
    }
}
