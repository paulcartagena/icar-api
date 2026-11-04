package com.paulcartagena.icarapi.donation.dto;

import com.paulcartagena.icarapi.donation.enums.DonationStatus;

import java.math.BigDecimal;
import java.util.UUID;

// What the public website gets back after paying: no donor or payer data
public record OnlineDonationResponse(
        UUID id,
        DonationStatus status,
        BigDecimal amount
) {
}
