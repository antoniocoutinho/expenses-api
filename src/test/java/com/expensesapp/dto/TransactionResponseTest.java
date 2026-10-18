package com.expensesapp.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.expensesapp.model.ExpenseType;
import com.expensesapp.model.InstallmentInfo;
import com.expensesapp.model.Transaction;
import com.expensesapp.model.TransactionKind;

/**
 * Unit tests for {@link TransactionResponse#from(Transaction)}.
 *
 * Spec 2.C: "Split?" column must display the installment count (e.g. "1/3")
 * or be blank when the transaction isn't part of an installment series.
 */
class TransactionResponseTest {

    @Test
    @DisplayName("splitLabel is null when the transaction has no installment info (income or fixed expense)")
    void splitLabelIsNullWithoutInstallments() {
        Transaction transaction = Transaction.builder()
                .id("1")
                .year(2026)
                .month(3)
                .description("Salary")
                .value(new BigDecimal("2000.00"))
                .kind(TransactionKind.INCOME)
                .installments(null)
                .build();

        TransactionResponse response = TransactionResponse.from(transaction);

        assertThat(response.getSplitLabel()).isNull();
    }

    @Test
    @DisplayName("splitLabel is formatted as 'index/total' when installment info is present")
    void splitLabelIsFormattedCorrectly() {
        Transaction transaction = Transaction.builder()
                .id("2")
                .year(2026)
                .month(4)
                .description("Laptop")
                .value(new BigDecimal("-400.00"))
                .kind(TransactionKind.EXPENSE)
                .expenseType(ExpenseType.VARIABLE)
                .installments(new InstallmentInfo("group-1", 2, 3))
                .build();

        TransactionResponse response = TransactionResponse.from(transaction);

        assertThat(response.getSplitLabel()).isEqualTo("2/3");
    }

    @Test
    @DisplayName("splitLabel handles the final installment of a series correctly")
    void splitLabelForFinalInstallment() {
        Transaction transaction = Transaction.builder()
                .id("3")
                .year(2026)
                .month(6)
                .description("Furniture")
                .value(new BigDecimal("-150.00"))
                .kind(TransactionKind.EXPENSE)
                .expenseType(ExpenseType.VARIABLE)
                .installments(new InstallmentInfo("group-2", 5, 5))
                .build();

        TransactionResponse response = TransactionResponse.from(transaction);

        assertThat(response.getSplitLabel()).isEqualTo("5/5");
    }

    @Test
    @DisplayName("all other fields are copied through unchanged")
    void copiesAllOtherFieldsUnchanged() {
        Transaction transaction = Transaction.builder()
                .id("4")
                .year(2027)
                .month(11)
                .description("Water bill")
                .value(new BigDecimal("-42.30"))
                .kind(TransactionKind.EXPENSE)
                .expenseType(ExpenseType.FIXED)
                .installments(null)
                .build();

        TransactionResponse response = TransactionResponse.from(transaction);

        assertThat(response.getId()).isEqualTo("4");
        assertThat(response.getYear()).isEqualTo(2027);
        assertThat(response.getMonth()).isEqualTo(11);
        assertThat(response.getDescription()).isEqualTo("Water bill");
        assertThat(response.getValue()).isEqualByComparingTo("-42.30");
        assertThat(response.getKind()).isEqualTo(TransactionKind.EXPENSE);
        assertThat(response.getExpenseType()).isEqualTo(ExpenseType.FIXED);
    }
}
