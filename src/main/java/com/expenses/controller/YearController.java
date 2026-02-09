package com.expenses.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.expenses.model.Expense;
import com.expenses.model.Year;
import com.expenses.service.YearService;

@RestController
@RequestMapping("/api/years")
//@CrossOrigin(origins = "http://localhost:4200") // Default Angular port
public class YearController {

    private final YearService yearService;

    public YearController(YearService yearService) {
        this.yearService = yearService;
    }

    // Fetches data for a specific year (including all months)
    @GetMapping("/{yearNumber}")
    public ResponseEntity<Year> getYear(@PathVariable int yearNumber) {
        return yearService.findByYear(yearNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Creates a new year with 12 initialized months
    @PostMapping("/{yearNumber}")
    public Year createYear(@PathVariable int yearNumber) {
        return yearService.createNewYear(yearNumber);
    }

    // Adds a fixed expense in a month and replicates it to all others
    @PostMapping("/{yearNumber}/replicate-fixed")
    public ResponseEntity<Year> addAndReplicateFixed(

            @PathVariable int yearNumber,
            @RequestBody Expense expense) {
        
        Year updatedYear = yearService.replicateFixedExpense(yearNumber, expense);
        return ResponseEntity.ok(updatedYear);
    }

    // Updates the income of a specific month
    @PatchMapping("/{yearNumber}/{monthName}/income")
    public ResponseEntity<Year> updateIncome(
            @PathVariable int yearNumber,
            @PathVariable String monthName,
            @RequestParam double value) {
        
        return ResponseEntity.ok(yearService.updateMonthIncome(yearNumber, monthName, value));
    }
}
