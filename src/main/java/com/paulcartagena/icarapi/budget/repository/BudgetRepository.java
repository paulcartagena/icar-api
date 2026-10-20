package com.paulcartagena.icarapi.budget.repository;

import com.paulcartagena.icarapi.budget.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID>, JpaSpecificationExecutor<Budget> {

    // Two periods overlap when each one starts before or on the day the other ends
    @Query("""
        SELECT b FROM Budget b
        WHERE b.category.id = :categoryId
          AND b.periodStart <= :periodEnd
          AND b.periodEnd >= :periodStart
    """)
    List<Budget> findOverlapping(@Param("categoryId") UUID categoryId,
                                 @Param("periodStart") LocalDate periodStart,
                                 @Param("periodEnd") LocalDate periodEnd);
}
