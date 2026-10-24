package com.paulcartagena.icarapi.family.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record FamilyRequest(
        @NotBlank(message = "Family name is required.")
        @Size(max = 100, message = "Family name must be at most 100 characters.")
        String familyName,

        @Size(max = 150, message = "Notes must be at most 150 characters.")
        String notes,

        @NotNull(message = "Responsible member is required.")
        UUID responsibleMemberId
) {
}
