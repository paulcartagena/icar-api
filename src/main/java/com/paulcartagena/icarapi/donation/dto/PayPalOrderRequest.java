package com.paulcartagena.icarapi.donation.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// An online donation about to be paid in PayPal. The min/max amount comes from configuration (checked in the service).
public record PayPalOrderRequest(
        @NotNull(message = "Amount is required.")
        @Positive(message = "Amount must be greater than 0.")
        @Digits(integer = 8, fraction = 2, message = "Amount must have at most 8 integer digits and 2 decimals.")
        BigDecimal amount,

        @Size(max = 150, message = "Donor name must be at most 150 characters.")
        String donorName,

        @Size(max = 500, message = "Message must be at most 500 characters.")
        String message
) {
}
