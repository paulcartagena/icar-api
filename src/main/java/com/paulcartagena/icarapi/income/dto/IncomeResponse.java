package com.paulcartagena.icarapi.income.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record IncomeResponse(
        UUID id,
        UUID categoryId,
        String categoryName,
        UUID sourceDonationId,
        BigDecimal amount,
        String description,
        LocalDate date,
        boolean voided,
        LocalDateTime voidedAt,
        String voidReason
) {
}
