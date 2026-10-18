package com.expensesapp.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.expensesapp.model.ExpenseType;
import com.expensesapp.model.Transaction;
import com.expensesapp.model.TransactionKind;

import lombok.Builder;
import lombok.Data;

/**
 * Response shape for a single row in the Transaction List (spec 2.C).
 * {@code splitLabel} is pre-formatted ("1/3") so the frontend doesn't need
 * installment math; null/blank when the transaction isn't split.
 */
@Data
@Builder
public class TransactionResponse {

    private String id;
    private int year;
    private int month;
    private String description;
    private TransactionKind kind;
    private ExpenseType expenseType;
    private BigDecimal value;
    private String splitLabel;
    private Instant createdAt;
    private Instant updatedAt;

    public static TransactionResponse from(Transaction t) {
        String splitLabel = null;
        if (t.getInstallments() != null) {
            splitLabel = t.getInstallments().getIndex() + "/" + t.getInstallments().getTotal();
        }
        return TransactionResponse.builder()
                .id(t.getId())
                .year(t.getYear())
                .month(t.getMonth())
                .description(t.getDescription())
                .kind(t.getKind())
                .expenseType(t.getExpenseType())
                .value(t.getValue())
                .splitLabel(splitLabel)
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
