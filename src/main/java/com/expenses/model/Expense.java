package com.expenses.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data // Generates Getters, Setters, toString, equals and hashCode
@NoArgsConstructor // No-argument constructor (mandatory for Spring Data)
@AllArgsConstructor // Constructor with all fields (useful for tests and quick creation)
public class Expense {

    private String description;
    private double value;
    private boolean paid;

    // The manual getValue() method that was here was removed
    // because @Data already generates an identical 'public double getValue()'.
}