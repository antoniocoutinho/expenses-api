package com.expensesapp.model;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cached rollup of one month's totals, embedded in {@link Year#getMonths()}.
 * This is a derived cache over {@code transactions}; source of truth always
 * remains the Transaction collection. Kept eventually-consistent by
 * TransactionService on every write.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthSummary {

    @Builder.Default
    private BigDecimal totalIncome = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal totalExpense = BigDecimal.ZERO;
}
