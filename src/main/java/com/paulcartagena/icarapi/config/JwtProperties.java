package com.paulcartagena.icarapi.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// HS256 needs a key of at least 256 bits; generate one with `openssl rand -base64 48`
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank
        @Size(min = 32, message = "jwt.secret must be at least 32 characters (256 bits).")
        String secret
) {
}
