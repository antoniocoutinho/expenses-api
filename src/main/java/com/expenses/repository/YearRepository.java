package com.expenses.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.expenses.model.Year;

public interface YearRepository extends MongoRepository<Year, String> {
    Optional<Year> findByYearNumber(int yearNumber);
}