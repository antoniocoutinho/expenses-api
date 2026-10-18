package com.expensesapp.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.expensesapp.dto.CreateExpenseRequest;
import com.expensesapp.dto.CreateIncomeRequest;
import com.expensesapp.dto.TransactionResponse;
import com.expensesapp.dto.UpdateExpenseRequest;
import com.expensesapp.service.TransactionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * Backs the Transaction List / Monthly Detail View (spec 2.C) and the
 * Add/Edit Expense and Add Income modals (spec 3.A/3.B/3.C).
 */
@RestController
@RequestMapping("/api/years/{year}/months/{month}/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    /** GET .../transactions -> rows for the Transaction List table. */
    @GetMapping
    public List<TransactionResponse> listTransactions(@PathVariable @Min(1) int year,
                                                        @PathVariable int month) {
        return transactionService.listTransactions(year, month);
    }

    /** GET .../transactions/balance -> "Total Monthly Balance" footer value. */
    @GetMapping("/balance")
    public Map<String, BigDecimal> getBalance(@PathVariable @Min(1) int year,
                                               @PathVariable int month) {
        return Map.of("balance", transactionService.getMonthlyBalance(year, month));
    }

    /**
     * POST .../transactions/expenses -> "Save Expense" in the Add Expense
     * modal. May return more than one row when the expense is split across
     * installments (spec 3.A conditional logic).
     */
    @PostMapping("/expenses")
    public ResponseEntity<List<TransactionResponse>> createExpense(@PathVariable @Min(1) int year,
                                                                     @PathVariable int month,
                                                                     @Valid @RequestBody CreateExpenseRequest request) {
        List<TransactionResponse> created = transactionService.createExpense(year, month, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** POST .../transactions/income -> "Save Income" in the Add Income modal. */
    @PostMapping("/income")
    public ResponseEntity<TransactionResponse> createIncome(@PathVariable @Min(1) int year,
                                                              @PathVariable int month,
                                                              @Valid @RequestBody CreateIncomeRequest request) {
        TransactionResponse created = transactionService.createIncome(year, month, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PUT .../transactions/{id} -> "Save Expense" in the Edit Expense modal
     * (spec 3.C). Path still carries year/month for routing/REST symmetry,
     * though the update itself is looked up by transaction id.
     */
    @PutMapping("/{id}")
    public TransactionResponse updateExpense(@PathVariable @Min(1) int year,
                                              @PathVariable int month,
                                              @PathVariable String id,
                                              @Valid @RequestBody UpdateExpenseRequest request) {
        return transactionService.updateExpense(id, request);
    }

    /** DELETE .../transactions/{id} -> "X" icon action on a row. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable @Min(1) int year,
                                                   @PathVariable int month,
                                                   @PathVariable String id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }
}
