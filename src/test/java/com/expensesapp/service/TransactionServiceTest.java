package com.expensesapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import com.expensesapp.dto.CreateExpenseRequest;
import com.expensesapp.dto.CreateIncomeRequest;
import com.expensesapp.dto.TransactionResponse;
import com.expensesapp.dto.UpdateExpenseRequest;
import com.expensesapp.exception.NotFoundException;
import com.expensesapp.model.ExpenseType;
import com.expensesapp.model.InstallmentInfo;
import com.expensesapp.model.Transaction;
import com.expensesapp.model.TransactionKind;
import com.expensesapp.model.Year;
import com.expensesapp.repository.TransactionRepository;

/**
 * Unit tests for {@link TransactionService}.
 *
 * Focus areas, mapped to spec sections:
 * - 3.A "Add Expense" modal, including the conditional installment-splitting
 *   logic and the year-rollover rule ("If the number of installments exceeds
 *   the remaining months of the current year, create a new year").
 * - 3.B "Add Income" modal.
 * - 3.C "Edit Expense" modal.
 * - 2.C transaction list / delete / monthly balance.
 *
 * YearService is mocked; its own behavior (cache math, year creation) is
 * covered separately in YearServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private YearService yearService;

    @InjectMocks
    private TransactionService transactionService;

    @Captor
    private ArgumentCaptor<List<Transaction>> transactionListCaptor;

    @Captor
    private ArgumentCaptor<Transaction> transactionCaptor;

    @BeforeEach
    void setUp() {
        // Default stubs: the repository echoes back what it receives, as if
        // Mongo had assigned ids. They are lenient because not every test
        // uses both save() and saveAll() (strict stubs would fail those).
        lenient().when(transactionRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Transaction> input = invocation.getArgument(0);
            for (Transaction t : input) {
                if (t.getId() == null) {
                    t.setId("generated-" + System.nanoTime());
                }
            }
            return input;
        });

        lenient().when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction t = invocation.getArgument(0);
            if (t.getId() == null) {
                t.setId("generated-" + System.nanoTime());
            }
            return t;
        });
    }

    // ------------------------------------------------------------------
    // 3.A Add Expense — Fixed expense (spec: no installment splitting at all)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("createExpense — Fixed expense")
    class FixedExpense {

        @Test
        @DisplayName("creates exactly one transaction in the requested month")
        void createsSingleTransactionForFixedExpense() {
            CreateExpenseRequest request = new CreateExpenseRequest();
            request.setDescription("Rent");
            request.setValue(new BigDecimal("1200.00"));
            request.setExpenseType(ExpenseType.FIXED);
            request.setInstallments(1);

            List<TransactionResponse> result = transactionService.createExpense(2026, 3, request);

            assertThat(result).hasSize(1);
            TransactionResponse tx = result.get(0);
            assertThat(tx.getYear()).isEqualTo(2026);
            assertThat(tx.getMonth()).isEqualTo(3);
            assertThat(tx.getDescription()).isEqualTo("Rent");
            assertThat(tx.getExpenseType()).isEqualTo(ExpenseType.FIXED);
            assertThat(tx.getKind()).isEqualTo(TransactionKind.EXPENSE);
            assertThat(tx.getSplitLabel()).isNull();
        }

        @Test
        @DisplayName("stores the value negated (expenses are negative, per spec 2.C sign convention)")
        void negatesValueForExpense() {
            CreateExpenseRequest request = new CreateExpenseRequest();
            request.setDescription("Electricity");
            request.setValue(new BigDecimal("85.50"));
            request.setExpenseType(ExpenseType.FIXED);
            request.setInstallments(1);

            List<TransactionResponse> result = transactionService.createExpense(2026, 5, request);

            assertThat(result.get(0).getValue()).isEqualByComparingTo("-85.50");
        }

        @Test
        @DisplayName("a Fixed expense is never split even if installments > 1 is supplied")
        void fixedExpenseIgnoresInstallmentsField() {
            // Spec 3.A: the Installments field is only enabled/relevant for
            // "Variable Expense". A Fixed expense must always be a single row,
            // regardless of what installments value happens to be present.
            CreateExpenseRequest request = new CreateExpenseRequest();
            request.setDescription("Insurance");
            request.setValue(new BigDecimal("300.00"));
            request.setExpenseType(ExpenseType.FIXED);
            request.setInstallments(5);

            List<TransactionResponse> result = transactionService.createExpense(2026, 1, request);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSplitLabel()).isNull();
        }

        @Test
        @DisplayName("updates the month summary cache with the negative signed value")
        void updatesMonthSummaryCache() {
            CreateExpenseRequest request = new CreateExpenseRequest();
            request.setDescription("Internet");
            request.setValue(new BigDecimal("60.00"));
            request.setExpenseType(ExpenseType.FIXED);
            request.setInstallments(1);

            transactionService.createExpense(2026, 4, request);

            verify(yearService).adjustMonthSummary(eq(2026), eq(4), eq(new BigDecimal("-60.00")));
        }
    }

    // ------------------------------------------------------------------
    // 3.A Add Expense — Variable expense with installments == 1 (not split)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Variable expense with installments == 1 behaves like a single, non-split expense")
    void variableExpenseWithOneInstallmentIsNotSplit() {
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("One-off gadget");
        request.setValue(new BigDecimal("49.99"));
        request.setExpenseType(ExpenseType.VARIABLE);
        request.setInstallments(1);

        List<TransactionResponse> result = transactionService.createExpense(2026, 6, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSplitLabel()).isNull();
    }

    // ------------------------------------------------------------------
    // 3.A Add Expense — Variable expense split across installments
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("createExpense — Variable expense with installments > 1")
    class InstallmentSplitting {

        @Test
        @DisplayName("creates one transaction per installment, each in a subsequent month")
        void splitsAcrossConsecutiveMonths() {
            CreateExpenseRequest request = new CreateExpenseRequest();
            request.setDescription("New laptop");
            request.setValue(new BigDecimal("1200.00"));
            request.setExpenseType(ExpenseType.VARIABLE);
            request.setInstallments(3);

            // Start in March 2026: installments land in Mar, Apr, May — no
            // year rollover needed.
            List<TransactionResponse> result = transactionService.createExpense(2026, 3, request);

            assertThat(result).hasSize(3);
            assertThat(result).extracting(TransactionResponse::getMonth).containsExactly(3, 4, 5);
            assertThat(result).allMatch(tx -> tx.getYear() == 2026);
            assertThat(result).extracting(TransactionResponse::getSplitLabel)
                    .containsExactly("1/3", "2/3", "3/3");
        }

        @Test
        @DisplayName("the total value is divided equally between the installments (negated)")
        void eachInstallmentIsTotalDividedByCount() {
            CreateExpenseRequest request = new CreateExpenseRequest();
            request.setDescription("Tuition");
            request.setValue(new BigDecimal("300.00"));
            request.setExpenseType(ExpenseType.VARIABLE);
            request.setInstallments(3);

            List<TransactionResponse> result = transactionService.createExpense(2026, 1, request);

            assertThat(result).allMatch(tx -> tx.getValue().compareTo(new BigDecimal("-100.00")) == 0);
        }

        @Test
        @DisplayName("when the total does not divide evenly, each installment is rounded to 2 decimals")
        void installmentValueIsRoundedToTwoDecimals() {
            CreateExpenseRequest request = new CreateExpenseRequest();
            request.setDescription("Laptop");
            request.setValue(new BigDecimal("1000.00"));
            request.setExpenseType(ExpenseType.VARIABLE);
            request.setInstallments(3);

            List<TransactionResponse> result = transactionService.createExpense(2026, 1, request);

            // 1000.00 / 3 = 333.333... -> 333.33 per installment
            assertThat(result).allMatch(tx -> tx.getValue().compareTo(new BigDecimal("-333.33")) == 0);
        }

        @Test
        @DisplayName("all installments share the same description and expense type")
        void allInstallmentsShareDescriptionAndType() {
            CreateExpenseRequest request = new CreateExpenseRequest();
            request.setDescription("Furniture");
            request.setValue(new BigDecimal("900.00"));
            request.setExpenseType(ExpenseType.VARIABLE);
            request.setInstallments(4);

            List<TransactionResponse> result = transactionService.createExpense(2026, 2, request);

            assertThat(result).allMatch(tx -> tx.getDescription().equals("Furniture"));
            assertThat(result).allMatch(tx -> tx.getExpenseType() == ExpenseType.VARIABLE);
        }

        @Test
        @DisplayName("all installments in the same series share one installment groupId")
        void allInstallmentsShareGroupId() {
            CreateExpenseRequest request = new CreateExpenseRequest();
            request.setDescription("Appliance");
            request.setValue(new BigDecimal("600.00"));
            request.setExpenseType(ExpenseType.VARIABLE);
            request.setInstallments(3);

            transactionService.createExpense(2026, 7, request);

            verify(transactionRepository).saveAll(transactionListCaptor.capture());
            List<Transaction> saved = transactionListCaptor.getValue();

            String firstGroupId = saved.get(0).getInstallments().getGroupId();
            assertThat(firstGroupId).isNotBlank();
            assertThat(saved).allMatch(t -> firstGroupId.equals(t.getInstallments().getGroupId()));
        }

        @Test
        @DisplayName("two separate installment purchases get two distinct groupIds")
        void differentPurchasesGetDifferentGroupIds() {
            CreateExpenseRequest first = new CreateExpenseRequest();
            first.setDescription("Sofa");
            first.setValue(new BigDecimal("500.00"));
            first.setExpenseType(ExpenseType.VARIABLE);
            first.setInstallments(2);

            CreateExpenseRequest second = new CreateExpenseRequest();
            second.setDescription("TV");
            second.setValue(new BigDecimal("800.00"));
            second.setExpenseType(ExpenseType.VARIABLE);
            second.setInstallments(2);

            List<TransactionResponse> firstResult = transactionService.createExpense(2026, 1, first);
            List<TransactionResponse> secondResult = transactionService.createExpense(2026, 1, second);

            // splitLabel doesn't expose groupId directly, so verify indirectly
            // via the repository capture across both calls.
            verify(transactionRepository, times(2)).saveAll(transactionListCaptor.capture());
            List<List<Transaction>> allSaves = transactionListCaptor.getAllValues();

            String groupIdOne = allSaves.get(0).get(0).getInstallments().getGroupId();
            String groupIdTwo = allSaves.get(1).get(0).getInstallments().getGroupId();

            assertThat(groupIdOne).isNotEqualTo(groupIdTwo);
            assertThat(firstResult).hasSize(2);
            assertThat(secondResult).hasSize(2);
        }

        @Test
        @DisplayName("updates the month summary cache once per generated installment")
        void updatesMonthSummaryForEachInstallment() {
            CreateExpenseRequest request = new CreateExpenseRequest();
            request.setDescription("Renovation");
            request.setValue(new BigDecimal("1000.00"));
            request.setExpenseType(ExpenseType.VARIABLE);
            request.setInstallments(3);

            transactionService.createExpense(2026, 1, request);

            // 1000.00 / 3 = 333.33 per installment
            verify(yearService).adjustMonthSummary(2026, 1, new BigDecimal("-333.33"));
            verify(yearService).adjustMonthSummary(2026, 2, new BigDecimal("-333.33"));
            verify(yearService).adjustMonthSummary(2026, 3, new BigDecimal("-333.33"));
            verify(yearService, times(3)).adjustMonthSummary(anyInt(), anyInt(), any());
        }
    }

    // ------------------------------------------------------------------
    // 3.A Add Expense — Year rollover rule
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("createExpense — installments rolling into a new year")
    class YearRollover {

        @Test
        @DisplayName("wraps December into January of the following year")
        void wrapsDecemberIntoNextJanuary() {
            // Spec 3.A: "If the number of installments exceeds the remaining
            // months of the current year, create a new year and update the
            // months of the new year accordingly."
            CreateExpenseRequest request = new CreateExpenseRequest();
            request.setDescription("Holiday gifts");
            request.setValue(new BigDecimal("450.00"));
            request.setExpenseType(ExpenseType.VARIABLE);
            request.setInstallments(3);

            // Starting in November 2026: Nov, Dec 2026, then Jan 2027.
            List<TransactionResponse> result = transactionService.createExpense(2026, 11, request);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).getYear()).isEqualTo(2026);
            assertThat(result.get(0).getMonth()).isEqualTo(11);
            assertThat(result.get(1).getYear()).isEqualTo(2026);
            assertThat(result.get(1).getMonth()).isEqualTo(12);
            assertThat(result.get(2).getYear()).isEqualTo(2027);
            assertThat(result.get(2).getMonth()).isEqualTo(1);
            assertThat(result.get(2).getSplitLabel()).isEqualTo("3/3");
        }

        @Test
        @DisplayName("ensures the rolled-over year exists before writing to it")
        void ensuresNextYearExistsBeforeWriting() {
            CreateExpenseRequest request = new CreateExpenseRequest();
            request.setDescription("Course fees");
            request.setValue(new BigDecimal("200.00"));
            request.setExpenseType(ExpenseType.VARIABLE);
            request.setInstallments(2);

            transactionService.createExpense(2026, 12, request);

            // Once for the starting year (2026), once for the rolled-over
            // year (2027) once month rolls past December.
            verify(yearService).ensureYearExists(2026);
            verify(yearService).ensureYearExists(2027);
        }

        @Test
        @DisplayName("can roll over more than one year boundary for a long installment series")
        void rollsOverMultipleYearBoundaries() {
            CreateExpenseRequest request = new CreateExpenseRequest();
            request.setDescription("Long-term loan payoff");
            request.setValue(new BigDecimal("100.00"));
            request.setExpenseType(ExpenseType.VARIABLE);
            request.setInstallments(15);

            // Starting November 2026: 2 months left in 2026 (Nov, Dec), then
            // all 12 months of 2027, then Jan 2028 for the 15th installment.
            List<TransactionResponse> result = transactionService.createExpense(2026, 11, request);

            assertThat(result).hasSize(15);
            assertThat(result.get(0).getYear()).isEqualTo(2026);
            assertThat(result.get(1).getYear()).isEqualTo(2026);
            // Installments 3..14 (index 2..13 zero-based) fall across 2027
            for (int i = 2; i <= 13; i++) {
                assertThat(result.get(i).getYear()).isEqualTo(2027);
            }
            assertThat(result.get(14).getYear()).isEqualTo(2028);
            assertThat(result.get(14).getMonth()).isEqualTo(1);
        }

        @Test
        @DisplayName("does not roll over when installments exactly fill remaining months of the year")
        void exactFitDoesNotRollOver() {
            CreateExpenseRequest request = new CreateExpenseRequest();
            request.setDescription("End of year subscription");
            request.setValue(new BigDecimal("30.00"));
            request.setExpenseType(ExpenseType.VARIABLE);
            request.setInstallments(3);

            // Starting October: Oct, Nov, Dec — fits exactly in 2026.
            List<TransactionResponse> result = transactionService.createExpense(2026, 10, request);

            assertThat(result).extracting(TransactionResponse::getYear).containsOnly(2026);
            assertThat(result).extracting(TransactionResponse::getMonth).containsExactly(10, 11, 12);
        }
    }

    // ------------------------------------------------------------------
    // 3.B Add Income
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("createIncome")
    class CreateIncome {

        @Test
        @DisplayName("creates a single income transaction with a positive value")
        void createsPositiveIncomeTransaction() {
            CreateIncomeRequest request = new CreateIncomeRequest();
            request.setDescription("Salary");
            request.setValue(new BigDecimal("3000.00"));

            TransactionResponse result = transactionService.createIncome(2026, 9, request);

            assertThat(result.getKind()).isEqualTo(TransactionKind.INCOME);
            assertThat(result.getValue()).isEqualByComparingTo("3000.00");
            assertThat(result.getDescription()).isEqualTo("Salary");
        }

        @Test
        @DisplayName("income has no expense type and no split label, per spec 3.B fields")
        void incomeHasNoTypeOrSplit() {
            CreateIncomeRequest request = new CreateIncomeRequest();
            request.setDescription("Freelance payment");
            request.setValue(new BigDecimal("500.00"));

            TransactionResponse result = transactionService.createIncome(2026, 9, request);

            assertThat(result.getExpenseType()).isNull();
            assertThat(result.getSplitLabel()).isNull();
        }

        @Test
        @DisplayName("updates the month summary cache with the positive signed value")
        void updatesMonthSummaryForIncome() {
            CreateIncomeRequest request = new CreateIncomeRequest();
            request.setDescription("Bonus");
            request.setValue(new BigDecimal("750.00"));

            transactionService.createIncome(2026, 9, request);

            verify(yearService).adjustMonthSummary(2026, 9, new BigDecimal("750.00"));
        }

        @Test
        @DisplayName("ensures the year exists before writing the income transaction")
        void ensuresYearExists() {
            CreateIncomeRequest request = new CreateIncomeRequest();
            request.setDescription("Gift");
            request.setValue(new BigDecimal("100.00"));

            transactionService.createIncome(2030, 1, request);

            verify(yearService).ensureYearExists(2030);
        }
    }

    // ------------------------------------------------------------------
    // 3.C Edit Expense
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("updateExpense")
    class UpdateExpense {

        @Test
        @DisplayName("updates description, value (negated) and expense type on the existing row")
        void updatesFieldsInPlace() {
            Transaction existing = Transaction.builder()
                    .id("tx-1")
                    .year(2026)
                    .month(4)
                    .description("Old description")
                    .value(new BigDecimal("-20.00"))
                    .kind(TransactionKind.EXPENSE)
                    .expenseType(ExpenseType.FIXED)
                    .build();

            when(transactionRepository.findById("tx-1")).thenReturn(Optional.of(existing));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UpdateExpenseRequest request = new UpdateExpenseRequest();
            request.setDescription("New description");
            request.setValue(new BigDecimal("35.00"));
            request.setExpenseType(ExpenseType.VARIABLE);

            TransactionResponse result = transactionService.updateExpense("tx-1", request);

            assertThat(result.getDescription()).isEqualTo("New description");
            assertThat(result.getValue()).isEqualByComparingTo("-35.00");
            assertThat(result.getExpenseType()).isEqualTo(ExpenseType.VARIABLE);
        }

        @Test
        @DisplayName("does not move the transaction to a different month or year")
        void doesNotChangeYearOrMonth() {
            Transaction existing = Transaction.builder()
                    .id("tx-2")
                    .year(2026)
                    .month(6)
                    .description("Groceries")
                    .value(new BigDecimal("-40.00"))
                    .kind(TransactionKind.EXPENSE)
                    .expenseType(ExpenseType.VARIABLE)
                    .build();

            when(transactionRepository.findById("tx-2")).thenReturn(Optional.of(existing));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UpdateExpenseRequest request = new UpdateExpenseRequest();
            request.setDescription("Groceries (corrected)");
            request.setValue(new BigDecimal("45.00"));
            request.setExpenseType(ExpenseType.VARIABLE);

            TransactionResponse result = transactionService.updateExpense("tx-2", request);

            assertThat(result.getYear()).isEqualTo(2026);
            assertThat(result.getMonth()).isEqualTo(6);
        }

        @Test
        @DisplayName("reverses the old contribution and applies the new one to the month summary cache")
        void updatesMonthSummaryContribution() {
            Transaction existing = Transaction.builder()
                    .id("tx-3")
                    .year(2026)
                    .month(2)
                    .description("Phone bill")
                    .value(new BigDecimal("-50.00"))
                    .kind(TransactionKind.EXPENSE)
                    .expenseType(ExpenseType.FIXED)
                    .build();

            when(transactionRepository.findById("tx-3")).thenReturn(Optional.of(existing));
            when(transactionRepository.save(any(Transaction.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            UpdateExpenseRequest request = new UpdateExpenseRequest();
            request.setDescription("Phone bill");
            request.setValue(new BigDecimal("65.00"));
            request.setExpenseType(ExpenseType.FIXED);

            transactionService.updateExpense("tx-3", request);

            verify(yearService).replaceMonthSummaryContribution(
                    2026, 2, new BigDecimal("-50.00"), new BigDecimal("-65.00"));
        }

        @Test
        @DisplayName("throws NotFoundException when the transaction id does not exist")
        void throwsWhenTransactionNotFound() {
            when(transactionRepository.findById("missing")).thenReturn(Optional.empty());

            UpdateExpenseRequest request = new UpdateExpenseRequest();
            request.setDescription("Whatever");
            request.setValue(new BigDecimal("10.00"));
            request.setExpenseType(ExpenseType.FIXED);

            assertThatThrownBy(() -> transactionService.updateExpense("missing", request))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    // ------------------------------------------------------------------
    // 2.C Delete transaction ("X" icon)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("deleteTransaction")
    class DeleteTransaction {

        @Test
        @DisplayName("deletes the transaction by id")
        void deletesById() {
            Transaction existing = Transaction.builder()
                    .id("tx-9")
                    .year(2026)
                    .month(3)
                    .description("Coffee")
                    .value(new BigDecimal("-4.50"))
                    .kind(TransactionKind.EXPENSE)
                    .expenseType(ExpenseType.VARIABLE)
                    .build();

            when(transactionRepository.findById("tx-9")).thenReturn(Optional.of(existing));

            transactionService.deleteTransaction("tx-9");

            verify(transactionRepository).deleteById("tx-9");
        }

        @Test
        @DisplayName("reverses the transaction's contribution from the month summary cache")
        void reversesMonthSummaryContribution() {
            Transaction existing = Transaction.builder()
                    .id("tx-10")
                    .year(2026)
                    .month(8)
                    .description("Streaming service")
                    .value(new BigDecimal("-15.00"))
                    .kind(TransactionKind.EXPENSE)
                    .expenseType(ExpenseType.FIXED)
                    .build();

            when(transactionRepository.findById("tx-10")).thenReturn(Optional.of(existing));

            transactionService.deleteTransaction("tx-10");

            verify(yearService).replaceMonthSummaryContribution(2026, 8, new BigDecimal("-15.00"), null);
        }

        @Test
        @DisplayName("throws NotFoundException when deleting a nonexistent transaction")
        void throwsWhenNotFound() {
            when(transactionRepository.findById("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.deleteTransaction("ghost"))
                    .isInstanceOf(NotFoundException.class);

            verify(transactionRepository, never()).deleteById(any());
        }
    }

    // ------------------------------------------------------------------
    // 2.C Transaction list & monthly balance
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("listTransactions and getMonthlyBalance")
    class ListingAndBalance {

        @Test
        @DisplayName("listTransactions maps repository results to response DTOs")
        void listsTransactionsForMonth() {
            Transaction expense = Transaction.builder()
                    .id("a").year(2026).month(3)
                    .description("Rent").value(new BigDecimal("-1000.00"))
                    .kind(TransactionKind.EXPENSE).expenseType(ExpenseType.FIXED)
                    .build();
            Transaction income = Transaction.builder()
                    .id("b").year(2026).month(3)
                    .description("Salary").value(new BigDecimal("2000.00"))
                    .kind(TransactionKind.INCOME)
                    .build();

            when(transactionRepository.findByYearAndMonthOrderByCreatedAtAsc(2026, 3))
                    .thenReturn(List.of(expense, income));

            List<TransactionResponse> result = transactionService.listTransactions(2026, 3);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(TransactionResponse::getDescription)
                    .containsExactly("Rent", "Salary");
        }

        @Test
        @DisplayName("getMonthlyBalance sums all signed transaction values for the month")
        void computesMonthlyBalanceAsSumOfSignedValues() {
            Transaction expense = Transaction.builder()
                    .id("a").year(2026).month(3)
                    .value(new BigDecimal("-300.00"))
                    .kind(TransactionKind.EXPENSE).expenseType(ExpenseType.FIXED)
                    .build();
            Transaction income = Transaction.builder()
                    .id("b").year(2026).month(3)
                    .value(new BigDecimal("1500.00"))
                    .kind(TransactionKind.INCOME)
                    .build();

            when(transactionRepository.findByYearAndMonthOrderByCreatedAtAsc(2026, 3))
                    .thenReturn(List.of(expense, income));

            BigDecimal balance = transactionService.getMonthlyBalance(2026, 3);

            assertThat(balance).isEqualByComparingTo("1200.00");
        }

        @Test
        @DisplayName("getMonthlyBalance is zero when there are no transactions")
        void balanceIsZeroWithNoTransactions() {
            when(transactionRepository.findByYearAndMonthOrderByCreatedAtAsc(2026, 11))
                    .thenReturn(List.of());

            BigDecimal balance = transactionService.getMonthlyBalance(2026, 11);

            assertThat(balance).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ------------------------------------------------------------------
    // Cross-cutting: installment count edge cases
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "installments={0} produces {0} transactions")
    @ValueSource(ints = {1, 2, 6, 12})
    @DisplayName("number of generated transactions always matches the requested installment count")
    void transactionCountMatchesInstallments(int installments) {
        CreateExpenseRequest request = new CreateExpenseRequest();
        request.setDescription("Variable purchase");
        request.setValue(new BigDecimal("120.00"));
        request.setExpenseType(ExpenseType.VARIABLE);
        request.setInstallments(installments);

        List<TransactionResponse> result = transactionService.createExpense(2026, 1, request);

        assertThat(result).hasSize(installments);
    }
}
