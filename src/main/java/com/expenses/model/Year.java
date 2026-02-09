package com.expenses.model;

import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Document(collection = "years")
@Data // Generates: getId, setId, getYearNumber, setYearNumber, getMonths, setMonths...
@NoArgsConstructor // Essential for Spring to instantiate the object when fetching from the database
@AllArgsConstructor // Useful for creating the complete object: new Year(null, 2024, monthList)
public class Year {

    @Id
    private String id;
    private int yearNumber;
    private List<MonthData> months;
}