package com.expensesapp.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** Payload for the "Add Income" modal (spec 3.B): just description + value. */
@Data
public class CreateIncomeRequest {

    @NotBlank
    private String description;

    @NotNull
    @Positive
    private BigDecimal value;
}
