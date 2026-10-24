package com.paulcartagena.icarapi.member.dto;

import com.paulcartagena.icarapi.member.enums.MemberStatus;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.UUID;

// familyId is optional: null means the member doesn't belong to any family
public record MemberRequest(
        @NotBlank(message = "First name is required.")
        @Size(max = 100, message = "First name must be at most 100 characters.")
        String firstName,

        @NotBlank(message = "Last name is required.")
        @Size(max = 100, message = "Last name must be at most 100 characters.")
        String lastName,

        @Email(message = "Email must be valid.")
        @Size(max = 150, message = "Email must be at most 150 characters.")
        String email,

        @Size(max = 25, message = "Phone must be at most 25 characters.")
        String phone,

        @NotNull(message = "Join date is required.")
        @PastOrPresent(message = "Join date cannot be in the future.")
        LocalDate joinDate,

        @NotNull(message = "Status is required.")
        MemberStatus status,

        UUID familyId
) {
}
