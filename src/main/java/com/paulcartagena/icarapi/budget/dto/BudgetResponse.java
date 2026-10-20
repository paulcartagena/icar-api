package com.paulcartagena.icarapi.budget.dto;

import com.paulcartagena.icarapi.category.enums.CategoryType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// actualAmount: non-voided expenses or incomes (by category type) of the category within the period.
// remainingAmount: plannedAmount - actualAmount; negative means the budget was exceeded.
public record BudgetResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        CategoryType categoryType,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal plannedAmount,
        BigDecimal actualAmount,
        BigDecimal remainingAmount
) {
}
