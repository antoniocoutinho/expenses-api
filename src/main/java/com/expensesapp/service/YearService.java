package com.expensesapp.service;

import java.math.BigDecimal;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.expensesapp.dto.MonthSummaryResponse;
import com.expensesapp.dto.YearResponse;
import com.expensesapp.exception.ConflictException;
import com.expensesapp.exception.NotFoundException;
import com.expensesapp.model.MonthSummary;
import com.expensesapp.model.Year;
import com.expensesapp.repository.TransactionRepository;
import com.expensesapp.repository.YearRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class YearService {

    private final YearRepository yearRepository;
    private final TransactionRepository transactionRepository;

    /** Year View dashboard (spec 2.A): all year cards, ascending. */
    public List<YearResponse> listYears() {
        return yearRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Year::getYear))
                .map(y -> YearResponse.builder().year(y.getYear()).build())
                .toList();
    }

    /** "+ Add Year" card action (spec 2.A). */
    public YearResponse createYear(int year) {
        if (yearRepository.existsByYear(year)) {
            throw new ConflictException("Year " + year + " already exists");
        }
        Year saved = yearRepository.save(Year.builder().year(year).build());
        return YearResponse.builder().year(saved.getYear()).build();
    }

    /**
     * Trash-can action on a year card (spec 2.A). Deletes the year and all
     * of its transactions — losing this data is destructive, so callers
     * (controller layer) should treat this as a hard delete with no
     * confirmation safety net beyond whatever the frontend adds.
     */
    public void deleteYear(int year) {
        if (!yearRepository.existsByYear(year)) {
            throw new NotFoundException("Year " + year + " not found");
        }
        transactionRepository.deleteByYear(year);
        yearRepository.deleteByYear(year);
    }

    /** Month View grid (spec 2.B): 12 cards with cached totals, defaulting to 0.00. */
    public List<MonthSummaryResponse> getMonthSummaries(int year) {
        Year yearDoc = yearRepository.findByYear(year)
                .orElseThrow(() -> new NotFoundException("Year " + year + " not found"));

        return java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(month -> {
                    MonthSummary summary = yearDoc.getMonths().getOrDefault(
                            String.valueOf(month),
                            MonthSummary.builder().build());
                    return MonthSummaryResponse.builder()
                            .month(month)
                            .monthName(Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                            .totalIncome(summary.getTotalIncome())
                            .totalExpense(summary.getTotalExpense())
                            .build();
                })
                .toList();
    }

    /** Ensures a Year document exists, creating it if not (used by installment rollover). */
    public Year ensureYearExists(int year) {
        return yearRepository.findByYear(year)
                .orElseGet(() -> yearRepository.save(Year.builder().year(year).build()));
    }

    /**
     * Applies a signed transaction value to the cached month summary for
     * (year, month), creating the Year/month entry if needed. Positive
     * delta = income increases; negative = expense increases (magnitude
     * tracked separately from the signed transaction value).
     */
    public void adjustMonthSummary(int year, int month, BigDecimal signedValue) {
        changeMonthSummary(year, month, signedValue, 1);
    }

    /**
     * Reverses a previously-applied signed value (used when deleting or
     * editing a transaction) then re-applies the new value, if given.
     * Passing {@code null} for newSignedValue just removes the old one.
     *
     * The old value is subtracted from the SAME bucket it was added to
     * (income for positive values, expense for negative values).
     */
    public void replaceMonthSummaryContribution(int year, int month,
                                                 BigDecimal oldSignedValue,
                                                 BigDecimal newSignedValue) {
        if (oldSignedValue != null) {
            changeMonthSummary(year, month, oldSignedValue, -1);
        }
        if (newSignedValue != null) {
            changeMonthSummary(year, month, newSignedValue, 1);
        }
    }

    /**
     * Adds (direction = 1) or removes (direction = -1) a signed transaction
     * value to/from the cached month summary. Positive values belong to
     * totalIncome, negative values belong to totalExpense (stored as a
     * positive magnitude).
     */
    private void changeMonthSummary(int year, int month, BigDecimal signedValue, int direction) {
        Year yearDoc = ensureYearExists(year);
        String key = String.valueOf(month);
        MonthSummary current = yearDoc.getMonths().getOrDefault(key, MonthSummary.builder().build());

        BigDecimal newIncome = current.getTotalIncome();
        BigDecimal newExpense = current.getTotalExpense();
        BigDecimal delta = signedValue.abs().multiply(BigDecimal.valueOf(direction));

        if (signedValue.signum() >= 0) {
            newIncome = newIncome.add(delta);
        } else {
            newExpense = newExpense.add(delta);
        }

        yearDoc.getMonths().put(key, MonthSummary.builder()
                .totalIncome(newIncome)
                .totalExpense(newExpense)
                .build());
        yearRepository.save(yearDoc);
    }
}