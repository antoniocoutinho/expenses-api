package com.expensesapp.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

/**
 * One card in the Month View grid (spec 2.B): month name plus totals,
 * defaulting to "0.00" when no data exists for that month yet.
 */
@Data
@Builder
public class MonthSummaryResponse {
    private int month;
    private String monthName;
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
}
