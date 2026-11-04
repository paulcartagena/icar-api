package com.paulcartagena.icarapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * PayPal checkout for online donations. Without a client id and secret the online donation endpoints answer 503,
 * so the app still starts (dev, CI) when PayPal isn't set up. The webhook also needs the webhook id.
 */
@ConfigurationProperties(prefix = "paypal")
public record PayPalProperties(
        @DefaultValue("") String clientId,
        @DefaultValue("") String clientSecret,
        // Shown in the app's webhook settings on developer.paypal.com; needed to verify webhook signatures
        @DefaultValue("") String webhookId,
        // Sandbox by default; https://api-m.paypal.com in production
        @DefaultValue("https://api-m.sandbox.paypal.com") String baseUrl,
        @DefaultValue("5.00") BigDecimal minAmount,
        @DefaultValue("5000.00") BigDecimal maxAmount,
        // Quick-pick buttons of the public website; any amount between min and max is also accepted
        @DefaultValue({"5", "10", "25", "50", "100"}) List<BigDecimal> presetAmounts,
        @DefaultValue OrderRateLimit orderRateLimit
) {

    public boolean configured() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    // Each client IP can create `capacity` PayPal orders, refilled gradually over `refillPeriod`
    public record OrderRateLimit(
            @DefaultValue("10") int capacity,
            @DefaultValue("1h") Duration refillPeriod
    ) {
    }
}
