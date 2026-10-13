package com.paulcartagena.icarapi.expense.repository;

import com.paulcartagena.icarapi.expense.dto.ExpenseCategoryTotal;
import com.paulcartagena.icarapi.expense.entity.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    @Query("""
        SELECT new 
        com.paulcartagena.icarapi.expense.dto.ExpenseCategoryTotal(
            c.id, c.name, SUM(e.amount), COUNT(e))
        FROM Expense e JOIN e.category c
        WHERE e.voided = false AND e.date BETWEEN :from AND :to
        GROUP BY c.id, c.name 
        ORDER BY SUM(e.amount) DESC
    """)
    List<ExpenseCategoryTotal> sumByCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
