package com.paulcartagena.icarapi.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        BigDecimal amount,
        String description,
        LocalDate date,
        String receiptUrl,
        boolean voided,
        LocalDateTime voidedAt,
        String voidReason
) {
}