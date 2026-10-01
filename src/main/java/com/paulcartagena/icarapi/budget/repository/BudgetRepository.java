package com.paulcartagena.icarapi.budget.repository;

import com.paulcartagena.icarapi.budget.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {
}
