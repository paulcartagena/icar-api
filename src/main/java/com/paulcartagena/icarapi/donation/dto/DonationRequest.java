package com.paulcartagena.icarapi.donation.dto;

import com.paulcartagena.icarapi.donation.enums.DonationMethod;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

// A donation received in person (cash or transfer). categoryId is the INCOME category of the income it creates.
public record DonationRequest(
        @Size(max = 150, message = "Donor name must be at most 150 characters.")
        String donorName,

        @Size(max = 500, message = "Message must be at most 500 characters.")
        String message,

        @NotNull(message = "Amount is required.")
        @Positive(message = "Amount must be greater than 0.")
        @Digits(integer = 8, fraction = 2, message = "Amount must have at most 8 integer digits and 2 decimals.")
        BigDecimal amount,

        @NotNull(message = "Method is required.")
        DonationMethod method,

        @NotNull(message = "Date is required.")
        @PastOrPresent(message = "Date cannot be in the future.")
        LocalDate date,

        @NotNull(message = "Category is required.")
        UUID categoryId
) {
}
