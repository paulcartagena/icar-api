package com.paulcartagena.icarapi.sitecontent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record AboutUsRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 250, message = "Title must be at most 250 characters.")
        String title,

        @NotBlank(message = "Body is required.")
        @Size(max = 10000, message = "Body must be at most 10000 characters.")
        String body,

        @NotBlank(message = "Image URL is required.")
        @Size(max = 500, message = "Image URL must be at most 500 characters.")
        @URL(message = "Image URL must be a valid URL.")
        String imageUrl
) {
}
