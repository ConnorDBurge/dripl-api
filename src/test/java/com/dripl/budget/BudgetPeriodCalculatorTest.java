package com.dripl.budget;

import com.dripl.budget.entity.Budget;
import com.dripl.budget.util.BudgetPeriodCalculator;
import com.dripl.budget.util.PeriodRange;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetPeriodCalculatorTest {

    @Nested
    class SingleAnchor {
        private final Budget budget = Budget.builder()
                .anchorDay1(1)
                .build();

        @Test
        void computePeriod_middleOfMonth() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 2, 15));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        void computePeriod_firstDayOfMonth() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 3, 1));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 3, 31));
        }

        @Test
        void computePeriod_lastDayOfMonth() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 1, 31));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 1, 31));
        }

        @Test
        void computePreviousPeriod() {
            PeriodRange march = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 3, 10));
            PeriodRange feb = BudgetPeriodCalculator.computePreviousPeriod(budget, march);
            assertThat(feb.start()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(feb.end()).isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        void computePeriod_leapYear_february() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2028, 2, 15));
            assertThat(range.end()).isEqualTo(LocalDate.of(2028, 2, 29));
        }

        @Test
        void computePeriod_anchorDay20_midCycle() {
            Budget b = Budget.builder().anchorDay1(20).build();
            PeriodRange range = BudgetPeriodCalculator.computePeriod(b, LocalDate.of(2026, 2, 25));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 2, 20));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 3, 19));
        }

        @Test
        void computePeriod_anchorDay20_beforeAnchor() {
            Budget b = Budget.builder().anchorDay1(20).build();
            PeriodRange range = BudgetPeriodCalculator.computePeriod(b, LocalDate.of(2026, 2, 10));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 1, 20));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 2, 19));
        }
    }

    @Nested
    class DualAnchor {
        private final Budget budget = Budget.builder()
                .anchorDay1(1)
                .anchorDay2(15)
                .build();

        @Test
        void computePeriod_firstHalf() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 2, 10));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 2, 14));
        }

        @Test
        void computePeriod_secondHalf() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 2, 20));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 2, 15));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        void computePeriod_onSecondAnchor() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 2, 15));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 2, 15));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        void computePeriod_lastDayOfMonth() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 3, 31));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 3, 15));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 3, 31));
        }

        @Test
        void computePreviousPeriod_fromSecondHalf() {
            PeriodRange secondHalf = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 2, 20));
            PeriodRange firstHalf = BudgetPeriodCalculator.computePreviousPeriod(budget, secondHalf);
            assertThat(firstHalf.start()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(firstHalf.end()).isEqualTo(LocalDate.of(2026, 2, 14));
        }

        @Test
        void computePreviousPeriod_fromFirstHalf() {
            PeriodRange firstHalf = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 3, 10));
            PeriodRange prevSecondHalf = BudgetPeriodCalculator.computePreviousPeriod(budget, firstHalf);
            assertThat(prevSecondHalf.start()).isEqualTo(LocalDate.of(2026, 2, 15));
            assertThat(prevSecondHalf.end()).isEqualTo(LocalDate.of(2026, 2, 28));
        }
    }

    @Nested
    class DualAnchorEndOfMonth {
        private final Budget budget = Budget.builder()
                .anchorDay1(15)
                .anchorDay2(31)
                .build();

        @Test
        void computePeriod_loPeriod_february() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 2, 20));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 2, 15));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 2, 27));
        }

        @Test
        void computePeriod_hiPeriod_february_crossesMonth() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 2, 28));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 3, 14));
        }

        @Test
        void computePeriod_hiPeriod_january() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 1, 31));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 1, 31));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 2, 14));
        }

        @Test
        void computePeriod_beforeFirstAnchor_crossesMonth() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 3, 10));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 3, 14));
        }

        @Test
        void navigation_february_forward() {
            PeriodRange p0 = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 2, 20));
            assertThat(p0).isEqualTo(new PeriodRange(LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 27)));

            PeriodRange p1 = BudgetPeriodCalculator.computeNextPeriod(budget, p0);
            assertThat(p1).isEqualTo(new PeriodRange(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 14)));

            PeriodRange p2 = BudgetPeriodCalculator.computeNextPeriod(budget, p1);
            assertThat(p2).isEqualTo(new PeriodRange(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 30)));

            PeriodRange p3 = BudgetPeriodCalculator.computeNextPeriod(budget, p2);
            assertThat(p3).isEqualTo(new PeriodRange(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 14)));
        }

        @Test
        void navigation_february_backward() {
            PeriodRange p0 = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 2, 20));
            assertThat(p0).isEqualTo(new PeriodRange(LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 27)));

            PeriodRange pMinus1 = BudgetPeriodCalculator.computePreviousPeriod(budget, p0);
            assertThat(pMinus1).isEqualTo(new PeriodRange(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 14)));

            PeriodRange pMinus2 = BudgetPeriodCalculator.computePreviousPeriod(budget, pMinus1);
            assertThat(pMinus2).isEqualTo(new PeriodRange(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 30)));

            PeriodRange pMinus3 = BudgetPeriodCalculator.computePreviousPeriod(budget, pMinus2);
            assertThat(pMinus3).isEqualTo(new PeriodRange(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 14)));
        }
    }

    @Nested
    class FixedInterval {
        private final Budget budget = Budget.builder()
                .intervalDays(14)
                .anchorDate(LocalDate.of(2026, 2, 6))
                .build();

        @Test
        void computePeriod_onAnchorDate() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 2, 6));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 2, 6));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 2, 19));
        }

        @Test
        void computePeriod_middleOfPeriod() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 2, 12));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 2, 6));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 2, 19));
        }

        @Test
        void computePeriod_firstDayOfNextPeriod() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 2, 20));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 2, 20));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 3, 5));
        }

        @Test
        void computePeriod_beforeAnchorDate() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 2, 1));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 1, 23));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 2, 5));
        }

        @Test
        void computePreviousPeriod() {
            PeriodRange current = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 2, 20));
            PeriodRange prev = BudgetPeriodCalculator.computePreviousPeriod(budget, current);
            assertThat(prev.start()).isEqualTo(LocalDate.of(2026, 2, 6));
            assertThat(prev.end()).isEqualTo(LocalDate.of(2026, 2, 19));
        }

        @Test
        void computePeriod_farInFuture() {
            PeriodRange range = BudgetPeriodCalculator.computePeriod(budget, LocalDate.of(2026, 6, 15));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 6, 12));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 6, 25));
        }

        @Test
        void computePeriod_weekly7days() {
            Budget weekly = Budget.builder()
                    .intervalDays(7)
                    .anchorDate(LocalDate.of(2026, 2, 6))
                    .build();
            PeriodRange range = BudgetPeriodCalculator.computePeriod(weekly, LocalDate.of(2026, 2, 18));
            assertThat(range.start()).isEqualTo(LocalDate.of(2026, 2, 13));
            assertThat(range.end()).isEqualTo(LocalDate.of(2026, 2, 19));
        }
    }
}
