package com.paulcartagena.icarapi.ministry.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDateTime;
import java.util.UUID;

// joinedAt is optional (defaults to now) so historical memberships can be loaded with their real date
public record MinistryMemberRequest(
        @NotNull(message = "Member is required.")
        UUID memberId,

        @PastOrPresent(message = "Joined at cannot be in the future.")
        LocalDateTime joinedAt
) {
}
