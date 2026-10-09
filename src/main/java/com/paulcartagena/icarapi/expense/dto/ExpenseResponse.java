package com.paulcartagena.icarapi.expense.dto;

import com.paulcartagena.icarapi.category.dto.CategoryResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        CategoryResponse category,
        BigDecimal amount,
        String description,
        LocalDate date,
        String receiptUrl
) {
}
