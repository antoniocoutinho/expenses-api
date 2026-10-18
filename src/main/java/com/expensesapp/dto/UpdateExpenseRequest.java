package com.expensesapp.dto;

import java.math.BigDecimal;

import com.expensesapp.model.ExpenseType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * Payload for the "Edit Expense" modal (spec 3.C). Edits a single existing
 * transaction row in place; it does not re-run installment splitting logic
 * (the spec only shows the edit modal pre-filled with the existing row's
 * fields, with no mention of re-splitting on edit).
 */
@Data
public class UpdateExpenseRequest {

    @NotBlank
    private String description;

    @NotNull
    @Positive
    private BigDecimal value;

    @NotNull
    private ExpenseType expenseType;
}
