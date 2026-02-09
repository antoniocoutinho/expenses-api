package com.expenses.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.expenses.model.Expense;
import com.expenses.model.MonthData;
import com.expenses.model.Year;
import com.expenses.repository.YearRepository;
import com.expenses.exceptions.ResourceAlreadyExistsException;
import com.expenses.exceptions.ResourceNotFoundException;

@Service
public class YearService {

    private final YearRepository repository;

    public YearService(YearRepository repository) {
        this.repository = repository;
    }

    // Finds a year or returns an error if it doesn't exist
    public Optional<Year> findByYear(int yearNumber) {
        return repository.findByYearNumber(yearNumber);
    }

    // Initializes a new year with the 12 default months
    public Year createNewYear(int yearNumber) {
        if (repository.findByYearNumber(yearNumber).isPresent()) {
            throw new ResourceAlreadyExistsException("Year " + yearNumber + " already exists in the system.");
        }

        Year year = new Year();
        year.setYearNumber(yearNumber);
        
        String[] monthNames = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        };

        List<MonthData> monthList = new ArrayList<>();
        for (String name : monthNames) {
            MonthData month = new MonthData();
            month.setMonthName(name);
            month.setTotalIncome(0.0);
            month.setFixedExpenses(new ArrayList<>());
            month.setVariableExpenses(new ArrayList<>());
            monthList.add(month);
        }
        
        year.setMonths(monthList);
        return repository.save(year);
    }

    // Business Rule: Add and Replicate Fixed Expenses
    public Year replicateFixedExpense(int yearNumber, Expense expense) {
        Year year = repository.findByYearNumber(yearNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Year " + yearNumber + " not found."));

        // Adds to all months of the document
        year.getMonths().forEach(month -> {
            // Criamos uma nova instância para evitar problemas de referência se necessário
            Expense newExpense = new Expense();
            newExpense.setDescription(expense.getDescription());
            newExpense.setValue(expense.getValue());
            newExpense.setPaid(expense.isPaid());
            
            month.getFixedExpenses().add(newExpense);
        });

        return repository.save(year);
    }

    // Adds variable expense to a specific month
    public Year addVariableExpense(int yearNumber, String monthName, Expense expense) {
        Year year = repository.findByYearNumber(yearNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Year " + yearNumber + " not found."));

        year.getMonths().stream()
            .filter(m -> m.getMonthName().equalsIgnoreCase(monthName))
            .findFirst()
            .ifPresent(m -> m.getVariableExpenses().add(expense));

        return repository.save(year);
    }

    // Updates the income for a specific month
    public Year updateMonthIncome(int yearNumber, String monthName, double income) {
        Year year = repository.findByYearNumber(yearNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Year " + yearNumber + " not found."));

        year.getMonths().stream()
            .filter(m -> m.getMonthName().equalsIgnoreCase(monthName))
            .findFirst()
            .ifPresent(m -> m.setTotalIncome(income));

        return repository.save(year);
    }

    // Deletes a specific expense from a month based on description, value, and type
    public Year deleteExpense(int yearNumber, String monthName, String expenseDescription, double expenseValue, String expenseType) {
        Year year = repository.findByYearNumber(yearNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Year " + yearNumber + " not found."));

        year.getMonths().stream()
            .filter(m -> m.getMonthName().equalsIgnoreCase(monthName))
            .findFirst()
            .ifPresent(monthData -> {
                List<Expense> expensesToModify;
                if ("fixed".equalsIgnoreCase(expenseType)) {
                    expensesToModify = monthData.getFixedExpenses();
                } else if ("variable".equalsIgnoreCase(expenseType)) {
                    expensesToModify = monthData.getVariableExpenses();
                } else {
                    throw new IllegalArgumentException("Invalid expense type. Must be 'fixed' or 'variable'.");
                }

                // Find and remove only the first matching expense
                Optional<Expense> expenseToRemove = expensesToModify.stream()
                    .filter(e -> e.getDescription().equals(expenseDescription) && e.getValue() == expenseValue)
                    .findFirst();
                
                expenseToRemove.ifPresent(expensesToModify::remove);
            });

        return repository.save(year);
    }
}
