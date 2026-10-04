package com.paulcartagena.icarapi.category.controller;

import com.paulcartagena.icarapi.category.dto.CategoryRequest;
import com.paulcartagena.icarapi.category.dto.CategoryResponse;
import com.paulcartagena.icarapi.category.dto.CategoryUpdateRequest;
import com.paulcartagena.icarapi.category.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
@Tag(name = "Categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> getAllCategories() {
        return categoryService.getAllCategories();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest categoryRequest) {
        return categoryService.createCategory(categoryRequest);
    }

    @PutMapping("/{id}")
    public CategoryResponse updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryUpdateRequest categoryUpdateRequest) {
        return categoryService.updateCategory(id, categoryUpdateRequest);
    }

    @PatchMapping("/{id}/activate")
    public CategoryResponse activateCategory(@PathVariable UUID id) {
        return categoryService.activateCategory(id);
    }

    @PatchMapping("/{id}/deactivate")
    public CategoryResponse deactivateCategory(@PathVariable UUID id) {
        return categoryService.deactivateCategory(id);
    }
}
