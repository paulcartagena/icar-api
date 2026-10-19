package com.paulcartagena.icarapi.user.dto;

import com.paulcartagena.icarapi.user.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Username is not editable: it's how the user logs in
public record UpdateUserRequest(
        @NotBlank(message = "Full name is required.")
        @Size(max = 250, message = "Full name must be at most 250 characters.")
        String fullName,

        @NotNull(message = "Role is required.")
        UserRole role
) {
}
