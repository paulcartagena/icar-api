package com.paulcartagena.icarapi.income.repository;

import com.paulcartagena.icarapi.income.dto.IncomeCategoryTotal;
import com.paulcartagena.icarapi.income.entity.Income;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface IncomeRepository extends JpaRepository<Income, UUID>, JpaSpecificationExecutor<Income> {

    @Query("""
        SELECT new 
        com.paulcartagena.icarapi.income.dto.IncomeCategoryTotal(
            c.id, c.name, SUM(i.amount), COUNT(i))
        FROM Income i JOIN i.category c
        WHERE i.voided = false AND i.date BETWEEN :from AND :to
        GROUP BY c.id, c.name 
        ORDER BY SUM(i.amount) DESC
    """)
    List<IncomeCategoryTotal> sumByCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
        SELECT COALESCE(SUM(i.amount), 0)
        FROM Income i
        WHERE i.voided = false AND i.category.id = :categoryId AND i.date BETWEEN :from AND :to
    """)
    BigDecimal sumByCategoryBetween(@Param("categoryId") UUID categoryId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);
}
