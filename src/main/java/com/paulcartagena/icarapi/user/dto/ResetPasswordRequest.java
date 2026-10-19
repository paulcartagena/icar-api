package com.paulcartagena.icarapi.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotNull(message = "New password is required.")
        @Size(min = 12, max = 72, message = "New password must be between 12 and 72 characters.")
        String newPassword
) {
}
