package com.expensesapp.dto;

import lombok.Builder;
import lombok.Data;

/** One card in the Year View dashboard (spec 2.A). */
@Data
@Builder
public class YearResponse {
    private int year;
}
