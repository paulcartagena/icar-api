package com.paulcartagena.icarapi.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Username is required.")
        @Size(max = 25, message = "Username must be at most 25 characters.")
        String username,

        // BCrypt only uses the first 72 bytes and Spring Security rejects longer passwords
        @NotBlank(message = "Password is required.")
        @Size(max = 72, message = "Password must be at most 72 characters.")
        String password
) {
}
