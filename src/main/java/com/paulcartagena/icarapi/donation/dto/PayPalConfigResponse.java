package com.paulcartagena.icarapi.donation.dto;

import java.math.BigDecimal;
import java.util.List;

// What the public website needs to show the PayPal button: the client id is public by design (it goes in the JS SDK URL)
public record PayPalConfigResponse(
        String clientId,
        String currency,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        List<BigDecimal> presetAmounts
) {
}
