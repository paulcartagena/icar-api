package com.paulcartagena.icarapi.income.repository;

import com.paulcartagena.icarapi.income.entity.Income;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.UUID;

public final class IncomeSpecifications {

    private IncomeSpecifications() {
    }

    public static Specification<Income> hasCategory(UUID categoryId) {
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Income> dateBetween(LocalDate from, LocalDate to) {
        return (root, query, cb) -> cb.between(root.get("date"), from, to);
    }
}
