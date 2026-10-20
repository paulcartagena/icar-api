package com.paulcartagena.icarapi.budget.repository;

import com.paulcartagena.icarapi.budget.entity.Budget;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

// Reusable filters for GET /budgets: each one is combined with .and() only when its param is present
public final class BudgetSpecifications {

    private BudgetSpecifications() {
    }

    public static Specification<Budget> hasCategory(UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    // Budgets whose period touches [from, to], even partially
    public static Specification<Budget> overlaps(LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.and(
                cb.lessThanOrEqualTo(root.get("periodStart"), to),
                cb.greaterThanOrEqualTo(root.get("periodEnd"), from)
        );
    }
}
