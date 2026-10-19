package com.paulcartagena.icarapi.user.dto;

import com.paulcartagena.icarapi.user.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "Username is required.")
        @Size(max = 25, message = "Username must be at most 25 characters.")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
                message = "Username can only contain letters, numbers, dots, hyphens and underscores.")
        String username,

        @NotBlank(message = "Full name is required.")
        @Size(max = 250, message = "Full name must be at most 250 characters.")
        String fullName,

        // BCrypt only uses the first 72 bytes and Spring Security rejects longer passwords
        @NotNull(message = "Password is required.")
        @Size(min = 12, max = 72, message = "Password must be between 12 and 72 characters.")
        String password,

        @NotNull(message = "Role is required.")
        UserRole role
) {
}
