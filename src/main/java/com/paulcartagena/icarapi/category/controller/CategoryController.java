package com.paulcartagena.icarapi.category.controller;

import com.paulcartagena.icarapi.category.dto.CategoryRequest;
import com.paulcartagena.icarapi.category.dto.CategoryResponse;
import com.paulcartagena.icarapi.category.dto.CategoryUpdateRequest;
import com.paulcartagena.icarapi.category.enums.CategoryType;
import com.paulcartagena.icarapi.category.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PreAuthorize("hasAuthority('FINANCE_READ')")
    @GetMapping
    public List<CategoryResponse> getAllCategories(@RequestParam(required = false) CategoryType type,
                                                   @RequestParam(required = false) Boolean active) {
        return categoryService.getAllCategories(type, active);
    }

    @PreAuthorize("hasAuthority('FINANCE_WRITE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest categoryRequest) {
        return categoryService.createCategory(categoryRequest);
    }

    @PreAuthorize("hasAuthority('FINANCE_WRITE')")
    @PutMapping("/{id}")
    public CategoryResponse updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryUpdateRequest categoryUpdateRequest) {
        return categoryService.updateCategory(id, categoryUpdateRequest);
    }

    @PreAuthorize("hasAuthority('FINANCE_WRITE')")
    @PatchMapping("/{id}/activate")
    public CategoryResponse activateCategory(@PathVariable UUID id) {
        return categoryService.activateCategory(id);
    }

    @PreAuthorize("hasAuthority('FINANCE_WRITE')")
    @PatchMapping("/{id}/deactivate")
    public CategoryResponse deactivateCategory(@PathVariable UUID id) {
        return categoryService.deactivateCategory(id);
    }
}
