package com.paulcartagena.icarapi.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record EventRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 250, message = "Title must be at most 250 characters.")
        String title,

        @NotBlank(message = "Description is required.")
        @Size(max = 5000, message = "Description must be at most 5000 characters.")
        String description,

        @NotNull(message = "Start is required.")
        LocalDateTime startAt,

        @NotNull(message = "End is required.")
        LocalDateTime endAt,

        @Size(max = 100, message = "Location must be at most 100 characters.")
        String location
) {
}
