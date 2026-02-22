package com.dripl.budget.util;

import com.dripl.budget.entity.Budget;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class BudgetPeriodCalculator {

    private BudgetPeriodCalculator() {}

    public static PeriodRange computePeriod(Budget budget, LocalDate date) {
        if (budget.isFixedIntervalMode()) {
            return computeFixedInterval(date, budget.getIntervalDays(), budget.getAnchorDate());
        }
        if (budget.getAnchorDay2() != null) {
            return computeDualAnchor(date, budget.getAnchorDay1(), budget.getAnchorDay2());
        }
        return computeSingleAnchor(date, budget.getAnchorDay1());
    }

    public static PeriodRange computePreviousPeriod(Budget budget, PeriodRange current) {
        return computePeriod(budget, current.start().minusDays(1));
    }

    public static PeriodRange computeNextPeriod(Budget budget, PeriodRange current) {
        return computePeriod(budget, current.end().plusDays(1));
    }

    public static PeriodRange computePeriodByOffset(Budget budget, int offset) {
        PeriodRange current = computePeriod(budget, LocalDate.now());
        if (offset == 0) return current;
        if (offset > 0) {
            for (int i = 0; i < offset; i++) {
                current = computeNextPeriod(budget, current);
            }
        } else {
            for (int i = 0; i < -offset; i++) {
                current = computePreviousPeriod(budget, current);
            }
        }
        return current;
    }

    // Single anchor: period runs from anchorDay to the day before anchorDay next month
    private static PeriodRange computeSingleAnchor(LocalDate date, int anchorDay) {
        int day = date.getDayOfMonth();
        int clampedAnchor = Math.min(anchorDay, date.lengthOfMonth());

        LocalDate start;
        if (day >= clampedAnchor) {
            start = date.withDayOfMonth(clampedAnchor);
        } else {
            // We're before the anchor day, so the period started in the previous month
            LocalDate prevMonth = date.minusMonths(1);
            start = prevMonth.withDayOfMonth(Math.min(anchorDay, prevMonth.lengthOfMonth()));
        }

        // End is a day before the anchor in the next month
        LocalDate nextAnchorMonth = start.plusMonths(1);
        LocalDate end = nextAnchorMonth.withDayOfMonth(Math.min(anchorDay, nextAnchorMonth.lengthOfMonth())).minusDays(1);
        return new PeriodRange(start, end);
    }

    // Dual anchor: periods alternate between anchor days, crossing month boundaries
    // e.g., anchors 15 & 31 → Jan 15–30, Jan 31–Feb 14, Feb 15–27, Feb 28–Mar 14
    private static PeriodRange computeDualAnchor(LocalDate date, int anchor1, int anchor2) {
        int lo = Math.min(anchor1, anchor2);
        int hi = Math.max(anchor1, anchor2);
        int day = date.getDayOfMonth();
        int lastDay = date.lengthOfMonth();

        int clampedLo = Math.min(lo, lastDay);
        int clampedHi = Math.min(hi, lastDay);

        if (day >= clampedHi) {
            // In the "hi" period: hi of this month to the day before lo of next month
            LocalDate start = date.withDayOfMonth(clampedHi);
            LocalDate nextMonth = date.plusMonths(1);
            int nextClampedLo = Math.min(lo, nextMonth.lengthOfMonth());
            LocalDate end = nextMonth.withDayOfMonth(nextClampedLo).minusDays(1);
            return new PeriodRange(start, end);
        } else if (day >= clampedLo) {
            // In the "lo" period: lo to the day before hi
            LocalDate start = date.withDayOfMonth(clampedLo);
            LocalDate end = date.withDayOfMonth(clampedHi).minusDays(1);
            return new PeriodRange(start, end);
        } else {
            // Before the first anchor — we're in the previous month's "hi" period
            LocalDate prevMonth = date.minusMonths(1);
            int prevClampedHi = Math.min(hi, prevMonth.lengthOfMonth());
            LocalDate start = prevMonth.withDayOfMonth(prevClampedHi);
            LocalDate end = date.withDayOfMonth(clampedLo).minusDays(1);
            return new PeriodRange(start, end);
        }
    }

    private static PeriodRange computeFixedInterval(LocalDate date, int intervalDays, LocalDate anchorDate) {
        long daysSinceAnchor = ChronoUnit.DAYS.between(anchorDate, date);
        long periodIndex;
        if (daysSinceAnchor >= 0) {
            periodIndex = daysSinceAnchor / intervalDays;
        } else {
            periodIndex = (daysSinceAnchor - intervalDays + 1) / intervalDays;
        }
        LocalDate start = anchorDate.plusDays(periodIndex * intervalDays);
        LocalDate end = start.plusDays(intervalDays - 1);
        return new PeriodRange(start, end);
    }
}
