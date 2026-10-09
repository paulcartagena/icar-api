package com.paulcartagena.icarapi.expense.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseRequest(
        @NotNull(message = "Category is required.")
        UUID categoryId,

        @NotNull(message = "Amount is required.")
        @Positive(message = "Amount must be greater than 0.")
        @Digits(integer = 8, fraction = 2, message = "Amount must have at most 8 integer digits and 2 decimals.")
        BigDecimal amount,

        @Size(max = 250, message = "Description must be at most 250 characters.")
        String description,

        @NotNull(message = "Date is required.")
        @PastOrPresent(message = "Date cannot be in the future.")
        LocalDate date,

        @Size(max = 500, message = "Receipt URL must be at most 500 characters.")
        @URL(message = "Receipt URL must be a valid URL.")
        String receiptUrl
) {
}
