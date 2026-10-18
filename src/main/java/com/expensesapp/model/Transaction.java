package com.expensesapp.model;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single income or expense line item for a given year/month.
 *
 * Sign convention: {@code value} is negative for expenses, positive for
 * income, so "Total Monthly Balance" (spec 2.C footer) is a plain sum, and
 * the UI can color by sign alone (spec 2.C "Value" column).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "transactions")
@CompoundIndexes({
        @CompoundIndex(name = "year_month_idx", def = "{'year': 1, 'month': 1}")
})
public class Transaction {

    @Id
    private String id;

    private int year;

    /** 1-12 */
    private int month;

    private String description;

    /** Negative for expenses, positive for income. */
    private BigDecimal value;

    private TransactionKind kind;

    /** Null for income rows. */
    private ExpenseType expenseType;

    /** Null unless this row is one installment of a multi-month expense. */
    private InstallmentInfo installments;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
