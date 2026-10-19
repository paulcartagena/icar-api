package com.paulcartagena.icarapi.auth.dto;

import com.paulcartagena.icarapi.user.enums.Permission;
import com.paulcartagena.icarapi.user.enums.UserRole;

import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID id,
        String username,
        String fullName,
        UserRole role,
        List<Permission> permissions
) {
}
