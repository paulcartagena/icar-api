package com.paulcartagena.icarapi.donation.dto;

import com.paulcartagena.icarapi.donation.enums.DonationMethod;
import com.paulcartagena.icarapi.donation.enums.DonationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// incomeId/categoryId/categoryName are null while the donation has no income (e.g. a future pending PayPal one)
public record DonationResponse(
        UUID id,
        String donorName,
        String message,
        BigDecimal amount,
        DonationMethod method,
        DonationStatus status,
        LocalDateTime completedAt,
        UUID incomeId,
        UUID categoryId,
        String categoryName,
        boolean voided,
        LocalDateTime voidedAt,
        String voidReason,
        LocalDateTime createdAt
) {
}
