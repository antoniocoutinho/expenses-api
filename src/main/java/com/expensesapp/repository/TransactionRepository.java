package com.expensesapp.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.expensesapp.model.Transaction;

public interface TransactionRepository extends MongoRepository<Transaction, String> {

    List<Transaction> findByYearAndMonthOrderByCreatedAtAsc(int year, int month);

    List<Transaction> findByYear(int year);

    List<Transaction> findByInstallmentsGroupId(String groupId);

    void deleteByYear(int year);
}
