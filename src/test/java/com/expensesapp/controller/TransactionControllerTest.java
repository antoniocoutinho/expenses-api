package com.expensesapp.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.expensesapp.dto.CreateExpenseRequest;
import com.expensesapp.dto.TransactionResponse;
import com.expensesapp.exception.NotFoundException;
import com.expensesapp.model.ExpenseType;
import com.expensesapp.model.TransactionKind;
import com.expensesapp.service.TransactionService;

/**
 * MockMvc tests for {@link TransactionController}, backing the Transaction
 * List (spec 2.C) and the Add/Edit Expense and Add Income modals
 * (spec 3.A/3.B/3.C).
 */
@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @Nested
    @DisplayName("GET /api/years/{year}/months/{month}/transactions")
    class ListTransactions {

        @Test
        @DisplayName("returns 200 with the transaction list")
        void returnsTransactionList() throws Exception {
            TransactionResponse tx = TransactionResponse.builder()
                    .id("1").year(2026).month(3)
                    .description("Rent").value(new BigDecimal("-1000.00"))
                    .kind(TransactionKind.EXPENSE).expenseType(ExpenseType.FIXED)
                    .build();
            when(transactionService.listTransactions(2026, 3)).thenReturn(List.of(tx));

            mockMvc.perform(get("/api/years/2026/months/3/transactions"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].description").value("Rent"));
        }
    }

    @Nested
    @DisplayName("GET /api/years/{year}/months/{month}/transactions/balance")
    class GetBalance {

        @Test
        @DisplayName("returns 200 with the monthly balance")
        void returnsBalance() throws Exception {
            when(transactionService.getMonthlyBalance(2026, 3)).thenReturn(new BigDecimal("1234.56"));

            mockMvc.perform(get("/api/years/2026/months/3/transactions/balance"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.balance").value(1234.56));
        }
    }

    @Nested
    @DisplayName("POST /api/years/{year}/months/{month}/transactions/expenses")
    class CreateExpense {

        @Test
        @DisplayName("returns 201 with the created transaction(s) for a valid Fixed expense")
        void returns201ForFixedExpense() throws Exception {
            TransactionResponse tx = TransactionResponse.builder()
                    .id("1").year(2026).month(3)
                    .description("Rent").value(new BigDecimal("-1000.00"))
                    .kind(TransactionKind.EXPENSE).expenseType(ExpenseType.FIXED)
                    .build();
            when(transactionService.createExpense(eq(2026), eq(3), any(CreateExpenseRequest.class)))
                    .thenReturn(List.of(tx));

            mockMvc.perform(post("/api/years/2026/months/3/transactions/expenses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "description": "Rent",
                                      "value": 1000.00,
                                      "expenseType": "FIXED",
                                      "installments": 1
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].description").value("Rent"));
        }

        @Test
        @DisplayName("returns 201 with multiple transactions for a split Variable expense")
        void returns201WithMultipleTransactionsForSplitExpense() throws Exception {
            TransactionResponse tx1 = TransactionResponse.builder()
                    .id("1").year(2026).month(3).description("Laptop")
                    .value(new BigDecimal("-400.00")).kind(TransactionKind.EXPENSE)
                    .expenseType(ExpenseType.VARIABLE).splitLabel("1/3").build();
            TransactionResponse tx2 = TransactionResponse.builder()
                    .id("2").year(2026).month(4).description("Laptop")
                    .value(new BigDecimal("-400.00")).kind(TransactionKind.EXPENSE)
                    .expenseType(ExpenseType.VARIABLE).splitLabel("2/3").build();
            TransactionResponse tx3 = TransactionResponse.builder()
                    .id("3").year(2026).month(5).description("Laptop")
                    .value(new BigDecimal("-400.00")).kind(TransactionKind.EXPENSE)
                    .expenseType(ExpenseType.VARIABLE).splitLabel("3/3").build();

            when(transactionService.createExpense(eq(2026), eq(3), any(CreateExpenseRequest.class)))
                    .thenReturn(List.of(tx1, tx2, tx3));

            mockMvc.perform(post("/api/years/2026/months/3/transactions/expenses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "description": "Laptop",
                                      "value": 400.00,
                                      "expenseType": "VARIABLE",
                                      "installments": 3
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].splitLabel").value("1/3"))
                    .andExpect(jsonPath("$[2].splitLabel").value("3/3"));
        }

        @Test
        @DisplayName("returns 400 when description is blank")
        void returns400WhenDescriptionBlank() throws Exception {
            mockMvc.perform(post("/api/years/2026/months/3/transactions/expenses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "description": "",
                                      "value": 100.00,
                                      "expenseType": "FIXED",
                                      "installments": 1
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when value is not positive")
        void returns400WhenValueNotPositive() throws Exception {
            mockMvc.perform(post("/api/years/2026/months/3/transactions/expenses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "description": "Something",
                                      "value": -5.00,
                                      "expenseType": "FIXED",
                                      "installments": 1
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when expenseType is missing")
        void returns400WhenExpenseTypeMissing() throws Exception {
            mockMvc.perform(post("/api/years/2026/months/3/transactions/expenses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "description": "Something",
                                      "value": 10.00,
                                      "installments": 1
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when installments is less than 1")
        void returns400WhenInstallmentsLessThanOne() throws Exception {
            mockMvc.perform(post("/api/years/2026/months/3/transactions/expenses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "description": "Something",
                                      "value": 10.00,
                                      "expenseType": "VARIABLE",
                                      "installments": 0
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/years/{year}/months/{month}/transactions/income")
    class CreateIncome {

        @Test
        @DisplayName("returns 201 with the created income transaction")
        void returns201ForValidIncome() throws Exception {
            TransactionResponse tx = TransactionResponse.builder()
                    .id("1").year(2026).month(3)
                    .description("Salary").value(new BigDecimal("3000.00"))
                    .kind(TransactionKind.INCOME)
                    .build();
            when(transactionService.createIncome(eq(2026), eq(3), any())).thenReturn(tx);

            mockMvc.perform(post("/api/years/2026/months/3/transactions/income")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "description": "Salary",
                                      "value": 3000.00
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.description").value("Salary"))
                    .andExpect(jsonPath("$.kind").value("INCOME"));
        }

        @Test
        @DisplayName("returns 400 when value is missing")
        void returns400WhenValueMissing() throws Exception {
            mockMvc.perform(post("/api/years/2026/months/3/transactions/income")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"description\": \"Salary\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/years/{year}/months/{month}/transactions/{id}")
    class UpdateExpense {

        @Test
        @DisplayName("returns 200 with the updated transaction")
        void returns200OnSuccess() throws Exception {
            TransactionResponse tx = TransactionResponse.builder()
                    .id("tx-1").year(2026).month(3)
                    .description("Updated rent").value(new BigDecimal("-1100.00"))
                    .kind(TransactionKind.EXPENSE).expenseType(ExpenseType.FIXED)
                    .build();
            when(transactionService.updateExpense(eq("tx-1"), any())).thenReturn(tx);

            mockMvc.perform(put("/api/years/2026/months/3/transactions/tx-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "description": "Updated rent",
                                      "value": 1100.00,
                                      "expenseType": "FIXED"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.description").value("Updated rent"));
        }

        @Test
        @DisplayName("returns 404 when the transaction does not exist")
        void returns404WhenMissing() throws Exception {
            when(transactionService.updateExpense(eq("missing"), any()))
                    .thenThrow(new NotFoundException("Transaction missing not found"));

            mockMvc.perform(put("/api/years/2026/months/3/transactions/missing")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "description": "Whatever",
                                      "value": 10.00,
                                      "expenseType": "FIXED"
                                    }
                                    """))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 400 when the edited value is not positive")
        void returns400WhenValueNotPositive() throws Exception {
            mockMvc.perform(put("/api/years/2026/months/3/transactions/tx-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "description": "Whatever",
                                      "value": 0,
                                      "expenseType": "FIXED"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/years/{year}/months/{month}/transactions/{id}")
    class DeleteTransaction {

        @Test
        @DisplayName("returns 204 on successful deletion")
        void returns204OnSuccess() throws Exception {
            mockMvc.perform(delete("/api/years/2026/months/3/transactions/tx-1"))
                    .andExpect(status().isNoContent());

            verify(transactionService).deleteTransaction("tx-1");
        }

        @Test
        @DisplayName("returns 404 when the transaction does not exist")
        void returns404WhenMissing() throws Exception {
            org.mockito.Mockito.doThrow(new NotFoundException("Transaction ghost not found"))
                    .when(transactionService).deleteTransaction("ghost");

            mockMvc.perform(delete("/api/years/2026/months/3/transactions/ghost"))
                    .andExpect(status().isNotFound());
        }
    }
}
