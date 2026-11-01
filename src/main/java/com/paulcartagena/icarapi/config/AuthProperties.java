package com.paulcartagena.icarapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("7d") Duration refreshTokenTtl,
        // Only false for local testing over plain http in browsers that don't treat localhost as secure
        @DefaultValue("true") boolean refreshCookieSecure,
        @DefaultValue LoginRateLimit loginRateLimit
) {

    // Each client IP gets `capacity` login attempts, refilled gradually over `refillPeriod`
    public record LoginRateLimit(
            @DefaultValue("5") int capacity,
            @DefaultValue("1m") Duration refillPeriod
    ) {
    }
}
