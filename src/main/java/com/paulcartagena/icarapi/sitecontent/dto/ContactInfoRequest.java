package com.paulcartagena.icarapi.sitecontent.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record ContactInfoRequest(
        @NotBlank(message = "Address is required.")
        @Size(max = 250, message = "Address must be at most 250 characters.")
        String address,

        @NotBlank(message = "Phone is required.")
        @Size(max = 25, message = "Phone must be at most 25 characters.")
        String phone,

        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be valid.")
        @Size(max = 150, message = "Email must be at most 150 characters.")
        String email,

        @NotBlank(message = "Map URL is required.")
        @Size(max = 500, message = "Map URL must be at most 500 characters.")
        @URL(message = "Map URL must be a valid URL.")
        String mapUrl
) {
}
