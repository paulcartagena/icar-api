package com.paulcartagena.icarapi.expense.repository;

import com.paulcartagena.icarapi.expense.entity.Expense;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findByDateBetween(LocalDate from, LocalDate to, Sort sort);
    List<Expense> findByCategoryId(UUID categoryId, Sort sort);
}
