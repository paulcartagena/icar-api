package com.paulcartagena.icarapi.category.dto;

import com.paulcartagena.icarapi.category.enums.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CategoryRequest {

    @NotBlank(message = "Name is required.")
    private String name;

    @NotNull(message = "Type is required")
    private CategoryType type;
}
