package com.expensesapp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes one installment's place within a multi-month "Variable Expense"
 * purchase (spec 3.A: "Split?" column shows e.g. "1/3").
 * Null on the Transaction when the transaction is not part of an
 * installment series (income, or a fixed/single-installment expense).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstallmentInfo {

    /** Shared across all rows belonging to the same original purchase. */
    private String groupId;

    /** 1-based position of this row within the series. */
    private int index;

    /** Total number of installments in the series. */
    private int total;
}
