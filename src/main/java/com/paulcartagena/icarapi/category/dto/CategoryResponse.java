package com.paulcartagena.icarapi.category.dto;

import com.paulcartagena.icarapi.category.enums.CategoryType;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        CategoryType type,
        boolean active
) {
}
