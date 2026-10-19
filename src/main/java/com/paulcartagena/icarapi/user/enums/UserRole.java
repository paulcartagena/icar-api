package com.paulcartagena.icarapi.user.enums;

import lombok.Getter;

import java.util.Set;

import static com.paulcartagena.icarapi.user.enums.Permission.*;

@Getter
public enum UserRole {
    ADMIN(Set.of(Permission.values())),
    PASTOR(Set.of(FINANCE_READ, FINANCE_WRITE, MEMBERS_READ, MEMBERS_WRITE, CONTENT_WRITE, USERS_MANAGE)),
    TREASURER(Set.of(FINANCE_READ, FINANCE_WRITE));

    private final Set<Permission> permissions;

    UserRole(Set<Permission> permissions) {
        this.permissions = permissions;
    }
}
