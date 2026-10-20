package com.paulcartagena.icarapi.budget.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetRequest(
        @NotNull(message = "Category is required.")
        UUID categoryId,

        @NotNull(message = "Period start is required.")
        LocalDate periodStart,

        @NotNull(message = "Period end is required.")
        LocalDate periodEnd,

        @NotNull(message = "Planned amount is required.")
        @Positive(message = "Planned amount must be greater than 0.")
        @Digits(integer = 8, fraction = 2, message = "Planned amount must have at most 8 integer digits and 2 decimals.")
        BigDecimal plannedAmount
) {
}
