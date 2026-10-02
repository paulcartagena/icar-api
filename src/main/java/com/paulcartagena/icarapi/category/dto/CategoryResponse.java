package com.paulcartagena.icarapi.category.dto;

import com.paulcartagena.icarapi.category.enums.CategoryType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class CategoryResponse {
    private UUID id;
    private String name;
    private CategoryType type;
    private boolean active;
}
