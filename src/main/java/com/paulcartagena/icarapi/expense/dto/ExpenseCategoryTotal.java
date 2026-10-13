package com.paulcartagena.icarapi.expense.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseCategoryTotal(
        UUID categoryId,
        String categoryName,
        BigDecimal total,
        Long count
) {
}
