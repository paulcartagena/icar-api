package com.paulcartagena.icarapi.user.enums;

import lombok.Getter;

import java.util.Set;

import static com.paulcartagena.icarapi.user.enums.Permission.*;

@Getter
public enum UserRole {
    ADMIN(3, Set.of(Permission.values())),
    PASTOR(2, Set.of(FINANCE_READ, FINANCE_WRITE, MEMBERS_READ, MEMBERS_WRITE, CONTENT_WRITE, USERS_MANAGE)),
    TREASURER(1, Set.of(FINANCE_READ, FINANCE_WRITE));

    // Higher level = more authority. Used by the anti-escalation rules in UserService.
    private final int level;
    private final Set<Permission> permissions;

    UserRole(int level, Set<Permission> permissions) {
        this.level = level;
        this.permissions = permissions;
    }

    public boolean outranks(UserRole other) {
        return level > other.level;
    }

    public boolean hasPermission(Permission permission) {
        return permissions.contains(permission);
    }
}
