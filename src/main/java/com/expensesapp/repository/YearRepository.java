package com.expensesapp.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.expensesapp.model.Year;

public interface YearRepository extends MongoRepository<Year, String> {

    Optional<Year> findByYear(int year);

    boolean existsByYear(int year);

    void deleteByYear(int year);
}
