package com.expensesapp.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One document per year shown as a card on the Year View dashboard
 * (spec 2.A). Also carries a cached per-month totals map used to render
 * the Month View grid (spec 2.B) without re-aggregating transactions on
 * every page load.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "years")
public class Year {

    @Id
    private String id;

    @Indexed(unique = true)
    private int year;

    /** Keyed by month number as string ("1".."12"); sparse, absent = no data yet. */
    @Builder.Default
    private Map<String, MonthSummary> months = new HashMap<>();

    @CreatedDate
    private Instant createdAt;
}
