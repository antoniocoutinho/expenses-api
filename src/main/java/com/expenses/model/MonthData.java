package com.expenses.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class MonthData {
    private String monthName;
    private double totalIncome;
    private List<Expense> fixedExpenses = new ArrayList<>();
    private List<Expense> variableExpenses = new ArrayList<>();
    
    // The balance will be calculated in the Get or via service
    public double getRemainingBalance() {
        double totalOut = fixedExpenses.stream().mapToDouble(Expense::getValue).sum() +
                          variableExpenses.stream().mapToDouble(Expense::getValue).sum();
        return totalIncome - totalOut;
    }
}