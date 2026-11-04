package com.paulcartagena.icarapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "visit-requests")
public record VisitRequestProperties(
        @DefaultValue RateLimit rateLimit
) {

    // Each client IP can send `capacity` visit requests, refilled gradually over `refillPeriod`. Kept loose enough
    // for people sharing an IP (church wifi, mobile carrier NAT) while stopping bots that flood the form.
    public record RateLimit(
            @DefaultValue("5") int capacity,
            @DefaultValue("1h") Duration refillPeriod
    ) {
    }
}
