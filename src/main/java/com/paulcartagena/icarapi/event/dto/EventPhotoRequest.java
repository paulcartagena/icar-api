package com.paulcartagena.icarapi.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

// displayOrder is optional: a new photo goes last, an updated one keeps its position
public record EventPhotoRequest(
        @NotBlank(message = "Image URL is required.")
        @Size(max = 500, message = "Image URL must be at most 500 characters.")
        @URL(message = "Image URL must be a valid URL.")
        String imageUrl,

        @Positive(message = "Display order must be positive.")
        Integer displayOrder
) {
}
