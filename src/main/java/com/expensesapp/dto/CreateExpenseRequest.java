package com.expensesapp.dto;

import java.math.BigDecimal;

import com.expensesapp.model.ExpenseType;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Payload for the "Add Expense" modal (spec 3.A).
 * {@code installments} is only meaningful when {@code expenseType} is
 * VARIABLE; the conditional "Installments" number input in the spec is
 * {@code installments} here, defaulting to 1 (a single, non-split expense).
 */
@Data
public class CreateExpenseRequest {

    @NotBlank
    private String description;

    /** Always provided as a positive magnitude; sign is applied server-side. */
    @NotNull
    @Positive
    private BigDecimal value;

    @NotNull
    private ExpenseType expenseType;

    /** Number of monthly installments. 1 means "not split". Only relevant for VARIABLE. */
    @Min(1)
    private int installments = 1;
}
