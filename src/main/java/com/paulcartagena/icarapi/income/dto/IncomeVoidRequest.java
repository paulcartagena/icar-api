package com.paulcartagena.icarapi.income.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncomeVoidRequest(
        @NotBlank(message = "Void reason is required.")
        @Size(max = 250, message = "Void reason must be at most 250 characters.")
        String reason
) {
}
