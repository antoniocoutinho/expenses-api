package com.expensesapp.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.expensesapp.dto.CreateExpenseRequest;
import com.expensesapp.dto.CreateIncomeRequest;
import com.expensesapp.dto.TransactionResponse;
import com.expensesapp.dto.UpdateExpenseRequest;
import com.expensesapp.exception.NotFoundException;
import com.expensesapp.model.ExpenseType;
import com.expensesapp.model.InstallmentInfo;
import com.expensesapp.model.Transaction;
import com.expensesapp.model.TransactionKind;
import com.expensesapp.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final YearService yearService;

    /** Transaction List for the Monthly Detail View (spec 2.C). */
    public List<TransactionResponse> listTransactions(int year, int month) {
        return transactionRepository.findByYearAndMonthOrderByCreatedAtAsc(year, month).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    /** "Total Monthly Balance" footer (spec 2.C): sum of all signed values. */
    public BigDecimal getMonthlyBalance(int year, int month) {
        return transactionRepository.findByYearAndMonthOrderByCreatedAtAsc(year, month).stream()
                .map(Transaction::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Handles the "Add Expense" modal (spec 3.A), including the
     * conditional installment-splitting logic:
     * <ul>
     *   <li>Fixed expense, or Variable with installments &lt;= 1: a single
     *       transaction row in the given month.</li>
     *   <li>Variable expense with installments &gt; 1: one row per month,
     *       starting at (year, month) and rolling forward. When a row's
     *       month would exceed December, it wraps to January of the next
     *       year — creating that Year document if it doesn't exist yet
     *       (spec: "create a new year and update the months of the new
     *       year accordingly").</li>
     * </ul>
     */
    public List<TransactionResponse> createExpense(int year, int month, CreateExpenseRequest request) {
        yearService.ensureYearExists(year);

        boolean isSplit = request.getExpenseType() == ExpenseType.VARIABLE && request.getInstallments() > 1;
        int totalInstallments = isSplit ? request.getInstallments() : 1;
        String groupId = isSplit ? UUID.randomUUID().toString() : null;

        BigDecimal splitedValue = request.getValue().divide(
                BigDecimal.valueOf(request.getInstallments()),
                RoundingMode.HALF_UP
        );
        BigDecimal negatedValue = splitedValue.negate();

        List<Transaction> toSave = new ArrayList<>();
        int cursorYear = year;
        int cursorMonth = month;

        for (int i = 1; i <= totalInstallments; i++) {
            if (cursorMonth > 12) {
                cursorMonth = 1;
                cursorYear++;
                yearService.ensureYearExists(cursorYear);
            }

            InstallmentInfo installmentInfo = isSplit
                    ? new InstallmentInfo(groupId, i, totalInstallments)
                    : null;

            Transaction transaction = Transaction.builder()
                    .year(cursorYear)
                    .month(cursorMonth)
                    .description(request.getDescription())
                    .value(negatedValue)
                    .kind(TransactionKind.EXPENSE)
                    .expenseType(request.getExpenseType())
                    .installments(installmentInfo)
                    .build();

            toSave.add(transaction);
            cursorMonth++;
        }

        List<Transaction> saved = transactionRepository.saveAll(toSave);
        saved.forEach(t -> yearService.adjustMonthSummary(t.getYear(), t.getMonth(), t.getValue()));

        return saved.stream().map(TransactionResponse::from).toList();
    }

    /** Handles the "Add Income" modal (spec 3.B): single row, positive value. */
    public TransactionResponse createIncome(int year, int month, CreateIncomeRequest request) {
        yearService.ensureYearExists(year);

        Transaction transaction = Transaction.builder()
                .year(year)
                .month(month)
                .description(request.getDescription())
                .value(request.getValue())
                .kind(TransactionKind.INCOME)
                .expenseType(null)
                .installments(null)
                .build();

        Transaction saved = transactionRepository.save(transaction);
        yearService.adjustMonthSummary(saved.getYear(), saved.getMonth(), saved.getValue());

        return TransactionResponse.from(saved);
    }

    /**
     * Handles the "Edit Expense" modal (spec 3.C): updates description,
     * value, and type on an existing row in place. Does not move the
     * transaction between months/years and does not re-split installments.
     */
    public TransactionResponse updateExpense(String id, UpdateExpenseRequest request) {
        Transaction existing = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction " + id + " not found"));

        BigDecimal oldValue = existing.getValue();
        BigDecimal newValue = request.getValue().negate();

        existing.setDescription(request.getDescription());
        existing.setValue(newValue);
        existing.setExpenseType(request.getExpenseType());

        Transaction saved = transactionRepository.save(existing);
        yearService.replaceMonthSummaryContribution(saved.getYear(), saved.getMonth(), oldValue, newValue);

        return TransactionResponse.from(saved);
    }

    /** "X" delete action on a transaction row (spec 2.C). */
    public void deleteTransaction(String id) {
        Transaction existing = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction " + id + " not found"));

        transactionRepository.deleteById(id);
        yearService.replaceMonthSummaryContribution(
                existing.getYear(), existing.getMonth(), existing.getValue(), null);
    }
}
