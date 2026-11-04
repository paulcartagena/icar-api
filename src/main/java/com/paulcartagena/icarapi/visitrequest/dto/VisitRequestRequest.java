package com.paulcartagena.icarapi.visitrequest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Sent by the "Planea tu visita" form of the public website
public record VisitRequestRequest(
        @NotBlank(message = "Full name is required.")
        @Size(max = 150, message = "Full name must be at most 150 characters.")
        String fullName,

        @NotBlank(message = "Phone is required.")
        @Size(max = 25, message = "Phone must be at most 25 characters.")
        String phone,

        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be valid.")
        @Size(max = 150, message = "Email must be at most 150 characters.")
        String email,

        @Size(max = 1000, message = "Message must be at most 1000 characters.")
        String message
) {
}
