package com.expensesapp.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.expensesapp.dto.MonthSummaryResponse;
import com.expensesapp.dto.YearResponse;
import com.expensesapp.service.YearService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

/**
 * Backs the Year View dashboard (spec 2.A) and Month View grid (spec 2.B).
 */
@RestController
@RequestMapping("/api/years")
@RequiredArgsConstructor
@Tag(name = "Years", description = "Year dashboard and month summaries")
public class YearController {

    private final YearService yearService;

    /** GET /api/years -> year cards for the dashboard. */
    @Operation(summary = "List all years", description = "Returns the year cards shown on the dashboard.")
    @GetMapping
    public List<YearResponse> listYears() {
        return yearService.listYears();
    }

    /** POST /api/years {"year": 2027} -> "+ Add Year" card action. */
    @Operation(summary = "Add a new year", description = "Creates a new, empty year (the \"+ Add Year\" card action).")
    @PostMapping
    public ResponseEntity<YearResponse> createYear(@RequestBody Map<String, Integer> body) {
        Integer year = body.get("year");
        if (year == null) {
            throw new IllegalArgumentException("year is required");
        }
        YearResponse created = yearService.createYear(year);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** DELETE /api/years/{year} -> trash-can icon action on a year card. */
    @Operation(summary = "Delete a year", description = "Deletes a year and all of its transactions.")
    @DeleteMapping("/{year}")
    public ResponseEntity<Void> deleteYear(@PathVariable @Min(1) int year) {
        yearService.deleteYear(year);
        return ResponseEntity.noContent().build();
    }

    /** GET /api/years/{year}/months -> the 12 month cards with totals. */
    @Operation(summary = "Get month summaries for a year", description = "Returns the 12 month cards with income/expense totals.")
    @GetMapping("/{year}/months")
    public List<MonthSummaryResponse> getMonths(@PathVariable @Min(1) int year) {
        return yearService.getMonthSummaries(year);
    }
}
