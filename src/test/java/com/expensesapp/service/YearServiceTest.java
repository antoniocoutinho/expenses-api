package com.expensesapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.expensesapp.dto.MonthSummaryResponse;
import com.expensesapp.dto.YearResponse;
import com.expensesapp.exception.ConflictException;
import com.expensesapp.exception.NotFoundException;
import com.expensesapp.model.MonthSummary;
import com.expensesapp.model.Year;
import com.expensesapp.repository.TransactionRepository;
import com.expensesapp.repository.YearRepository;

/**
 * Unit tests for {@link YearService}.
 *
 * Maps to spec sections:
 * - 2.A Year View dashboard: list years, add year, delete year (cascade).
 * - 2.B Month View grid: 12 month cards, "0.00" default when no data exists.
 * - Cache maintenance used internally by TransactionService.
 */
@ExtendWith(MockitoExtension.class)
class YearServiceTest {

    @Mock
    private YearRepository yearRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private YearService yearService;

    @Captor
    private ArgumentCaptor<Year> yearCaptor;

    // ------------------------------------------------------------------
    // 2.A Year View dashboard
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("listYears")
    class ListYears {

        @Test
        @DisplayName("returns all years sorted ascending, regardless of storage order")
        void returnsYearsSortedAscending() {
            Year y2027 = Year.builder().year(2027).months(new HashMap<>()).build();
            Year y2025 = Year.builder().year(2025).months(new HashMap<>()).build();
            Year y2026 = Year.builder().year(2026).months(new HashMap<>()).build();

            when(yearRepository.findAll()).thenReturn(List.of(y2027, y2025, y2026));

            List<YearResponse> result = yearService.listYears();

            assertThat(result).extracting(YearResponse::getYear).containsExactly(2025, 2026, 2027);
        }

        @Test
        @DisplayName("returns an empty list when there are no years yet")
        void returnsEmptyListWhenNoYears() {
            when(yearRepository.findAll()).thenReturn(List.of());

            List<YearResponse> result = yearService.listYears();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("createYear")
    class CreateYear {

        @Test
        @DisplayName("creates a new year when it does not already exist")
        void createsNewYear() {
            when(yearRepository.existsByYear(2028)).thenReturn(false);
            when(yearRepository.save(any(Year.class))).thenAnswer(inv -> inv.getArgument(0));

            YearResponse result = yearService.createYear(2028);

            assertThat(result.getYear()).isEqualTo(2028);
            verify(yearRepository).save(yearCaptor.capture());
            assertThat(yearCaptor.getValue().getYear()).isEqualTo(2028);
        }

        @Test
        @DisplayName("throws ConflictException when the year already exists")
        void throwsConflictWhenYearExists() {
            when(yearRepository.existsByYear(2026)).thenReturn(true);

            assertThatThrownBy(() -> yearService.createYear(2026))
                    .isInstanceOf(ConflictException.class);

            verify(yearRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteYear")
    class DeleteYear {

        @Test
        @DisplayName("deletes the year and cascades to delete all its transactions")
        void deletesYearAndCascadesTransactions() {
            when(yearRepository.existsByYear(2026)).thenReturn(true);

            yearService.deleteYear(2026);

            verify(transactionRepository).deleteByYear(2026);
            verify(yearRepository).deleteByYear(2026);
        }

        @Test
        @DisplayName("throws NotFoundException when deleting a year that does not exist")
        void throwsWhenYearMissing() {
            when(yearRepository.existsByYear(1999)).thenReturn(false);

            assertThatThrownBy(() -> yearService.deleteYear(1999))
                    .isInstanceOf(NotFoundException.class);

            verify(transactionRepository, never()).deleteByYear(anyInt());
        }
    }

    // ------------------------------------------------------------------
    // 2.B Month View grid
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("getMonthSummaries")
    class GetMonthSummaries {

        @Test
        @DisplayName("returns exactly 12 month cards for a year")
        void returnsTwelveMonths() {
            Year year = Year.builder().year(2026).months(new HashMap<>()).build();
            when(yearRepository.findByYear(2026)).thenReturn(Optional.of(year));

            List<MonthSummaryResponse> result = yearService.getMonthSummaries(2026);

            assertThat(result).hasSize(12);
            assertThat(result).extracting(MonthSummaryResponse::getMonth)
                    .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        }

        @Test
        @DisplayName("defaults a month with no data to 0.00 / 0.00, per spec 2.B display logic")
        void defaultsMissingMonthsToZero() {
            Year year = Year.builder().year(2026).months(new HashMap<>()).build();
            when(yearRepository.findByYear(2026)).thenReturn(Optional.of(year));

            List<MonthSummaryResponse> result = yearService.getMonthSummaries(2026);

            assertThat(result).allSatisfy(month -> {
                assertThat(month.getTotalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(month.getTotalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
            });
        }

        @Test
        @DisplayName("surfaces existing cached totals for months that do have data")
        void surfacesExistingTotals() {
            Map<String, MonthSummary> months = new HashMap<>();
            months.put("3", MonthSummary.builder()
                    .totalIncome(new BigDecimal("1500.00"))
                    .totalExpense(new BigDecimal("320.50"))
                    .build());
            Year year = Year.builder().year(2026).months(months).build();
            when(yearRepository.findByYear(2026)).thenReturn(Optional.of(year));

            List<MonthSummaryResponse> result = yearService.getMonthSummaries(2026);

            MonthSummaryResponse march = result.stream()
                    .filter(m -> m.getMonth() == 3)
                    .findFirst()
                    .orElseThrow();

            assertThat(march.getTotalIncome()).isEqualByComparingTo("1500.00");
            assertThat(march.getTotalExpense()).isEqualByComparingTo("320.50");
        }

        @Test
        @DisplayName("includes the human-readable month name")
        void includesMonthName() {
            Year year = Year.builder().year(2026).months(new HashMap<>()).build();
            when(yearRepository.findByYear(2026)).thenReturn(Optional.of(year));

            List<MonthSummaryResponse> result = yearService.getMonthSummaries(2026);

            assertThat(result.get(0).getMonthName()).isEqualTo("January");
            assertThat(result.get(11).getMonthName()).isEqualTo("December");
        }

        @Test
        @DisplayName("throws NotFoundException for a year that doesn't exist")
        void throwsWhenYearMissing() {
            when(yearRepository.findByYear(1900)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> yearService.getMonthSummaries(1900))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    // ------------------------------------------------------------------
    // Cache maintenance (used internally by TransactionService)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("ensureYearExists")
    class EnsureYearExists {

        @Test
        @DisplayName("returns the existing year without creating a duplicate")
        void returnsExistingYear() {
            Year existing = Year.builder().year(2026).months(new HashMap<>()).build();
            when(yearRepository.findByYear(2026)).thenReturn(Optional.of(existing));

            Year result = yearService.ensureYearExists(2026);

            assertThat(result).isSameAs(existing);
            verify(yearRepository, never()).save(any());
        }

        @Test
        @DisplayName("creates the year when it doesn't exist yet")
        void createsYearWhenMissing() {
            when(yearRepository.findByYear(2030)).thenReturn(Optional.empty());
            when(yearRepository.save(any(Year.class))).thenAnswer(inv -> inv.getArgument(0));

            Year result = yearService.ensureYearExists(2030);

            assertThat(result.getYear()).isEqualTo(2030);
            verify(yearRepository).save(any(Year.class));
        }
    }

    @Nested
    @DisplayName("adjustMonthSummary")
    class AdjustMonthSummary {

        @Test
        @DisplayName("adds a positive value to totalIncome")
        void addsPositiveValueToIncome() {
            Year year = Year.builder().year(2026).months(new HashMap<>()).build();
            when(yearRepository.findByYear(2026)).thenReturn(Optional.of(year));
            when(yearRepository.save(any(Year.class))).thenAnswer(inv -> inv.getArgument(0));

            yearService.adjustMonthSummary(2026, 5, new BigDecimal("1000.00"));

            verify(yearRepository).save(yearCaptor.capture());
            MonthSummary may = yearCaptor.getValue().getMonths().get("5");
            assertThat(may.getTotalIncome()).isEqualByComparingTo("1000.00");
            assertThat(may.getTotalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("adds the absolute value of a negative amount to totalExpense")
        void addsAbsoluteValueOfNegativeAmountToExpense() {
            Year year = Year.builder().year(2026).months(new HashMap<>()).build();
            when(yearRepository.findByYear(2026)).thenReturn(Optional.of(year));
            when(yearRepository.save(any(Year.class))).thenAnswer(inv -> inv.getArgument(0));

            yearService.adjustMonthSummary(2026, 5, new BigDecimal("-250.00"));

            verify(yearRepository).save(yearCaptor.capture());
            MonthSummary may = yearCaptor.getValue().getMonths().get("5");
            assertThat(may.getTotalExpense()).isEqualByComparingTo("250.00");
            assertThat(may.getTotalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("accumulates multiple adjustments to the same month")
        void accumulatesMultipleAdjustments() {
            Map<String, MonthSummary> months = new HashMap<>();
            months.put("5", MonthSummary.builder()
                    .totalIncome(new BigDecimal("100.00"))
                    .totalExpense(new BigDecimal("50.00"))
                    .build());
            Year year = Year.builder().year(2026).months(months).build();
            when(yearRepository.findByYear(2026)).thenReturn(Optional.of(year));
            when(yearRepository.save(any(Year.class))).thenAnswer(inv -> inv.getArgument(0));

            yearService.adjustMonthSummary(2026, 5, new BigDecimal("-25.00"));

            verify(yearRepository).save(yearCaptor.capture());
            MonthSummary may = yearCaptor.getValue().getMonths().get("5");
            assertThat(may.getTotalIncome()).isEqualByComparingTo("100.00");
            assertThat(may.getTotalExpense()).isEqualByComparingTo("75.00");
        }
    }

    @Nested
    @DisplayName("replaceMonthSummaryContribution")
    class ReplaceMonthSummaryContribution {

        @Test
        @DisplayName("reverses the old value and applies the new one (edit scenario)")
        void reversesOldAndAppliesNew() {
            Map<String, MonthSummary> months = new HashMap<>();
            months.put("2", MonthSummary.builder()
                    .totalIncome(BigDecimal.ZERO)
                    .totalExpense(new BigDecimal("50.00"))
                    .build());
            Year year = Year.builder().year(2026).months(months).build();
            when(yearRepository.findByYear(2026)).thenReturn(Optional.of(year));
            when(yearRepository.save(any(Year.class))).thenAnswer(inv -> inv.getArgument(0));

            // Old value was -50 (expense), new value is -65 (expense).
            yearService.replaceMonthSummaryContribution(
                    2026, 2, new BigDecimal("-50.00"), new BigDecimal("-65.00"));

            // Two saves happen: one to reverse, one to apply. We just check
            // the final state via the last captured save.
            verify(yearRepository, times(2)).save(yearCaptor.capture());
            MonthSummary finalState = yearCaptor.getValue().getMonths().get("2");
            assertThat(finalState.getTotalExpense()).isEqualByComparingTo("65.00");
        }

        @Test
        @DisplayName("only reverses when the new value is null (delete scenario)")
        void onlyReversesWhenNewValueIsNull() {
            Map<String, MonthSummary> months = new HashMap<>();
            months.put("8", MonthSummary.builder()
                    .totalIncome(BigDecimal.ZERO)
                    .totalExpense(new BigDecimal("15.00"))
                    .build());
            Year year = Year.builder().year(2026).months(months).build();
            when(yearRepository.findByYear(2026)).thenReturn(Optional.of(year));
            when(yearRepository.save(any(Year.class))).thenAnswer(inv -> inv.getArgument(0));

            yearService.replaceMonthSummaryContribution(2026, 8, new BigDecimal("-15.00"), null);

            verify(yearRepository, times(1)).save(yearCaptor.capture());
            MonthSummary finalState = yearCaptor.getValue().getMonths().get("8");
            assertThat(finalState.getTotalExpense()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("only applies when the old value is null (pure creation scenario)")
        void onlyAppliesWhenOldValueIsNull() {
            Year year = Year.builder().year(2026).months(new HashMap<>()).build();
            when(yearRepository.findByYear(2026)).thenReturn(Optional.of(year));
            when(yearRepository.save(any(Year.class))).thenAnswer(inv -> inv.getArgument(0));

            yearService.replaceMonthSummaryContribution(2026, 4, null, new BigDecimal("200.00"));

            verify(yearRepository, times(1)).save(yearCaptor.capture());
            MonthSummary finalState = yearCaptor.getValue().getMonths().get("4");
            assertThat(finalState.getTotalIncome()).isEqualByComparingTo("200.00");
        }
    }
}
