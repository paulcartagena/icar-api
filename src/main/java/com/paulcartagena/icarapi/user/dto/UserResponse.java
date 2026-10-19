package com.paulcartagena.icarapi.user.dto;

import com.paulcartagena.icarapi.user.enums.UserRole;
import com.paulcartagena.icarapi.user.enums.UserStatus;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String fullName,
        UserRole role,
        UserStatus status
) {
}
