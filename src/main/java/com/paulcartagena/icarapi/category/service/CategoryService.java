package com.paulcartagena.icarapi.category.service;

import com.paulcartagena.icarapi.category.dto.CategoryRequest;
import com.paulcartagena.icarapi.category.dto.CategoryResponse;
import com.paulcartagena.icarapi.category.dto.CategoryUpdateRequest;
import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.category.enums.CategoryType;
import com.paulcartagena.icarapi.category.repository.CategoryRepository;
import com.paulcartagena.icarapi.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories(CategoryType type, Boolean active) {
        List<Category> categories;

        // Both filters are optional: null means "don't filter by it"
        if (type == null && active == null) {
            categories = categoryRepository.findAll();
        } else if (type != null && active == null) {
            categories = categoryRepository.findByType(type);
        } else if (type == null) {
            categories = categoryRepository.findByActive(active);
        } else {
            categories = categoryRepository.findByTypeAndActive(type, active);
        }

        return categories.stream()
                .map(this::buildResponse)
                .toList();
    }

    public CategoryResponse createCategory(CategoryRequest categoryRequest) {
        String name = categoryRequest.name().trim();

        // Validation: unique (case-insensitive)
        if (categoryRepository.existsByNameIgnoreCaseAndType(name, categoryRequest.type())) {
            throw ApiException.duplicateResource("Category already exists.");
        }

        Category category = new Category();
        category.setName(name);
        category.setType(categoryRequest.type());
        category.setActive(true);

        Category savedCategory = categoryRepository.save(category);
        return buildResponse(savedCategory);
    }

    public CategoryResponse updateCategory(UUID id, CategoryUpdateRequest categoryUpdateRequest) {

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.resourceNotFound("Category not found: " + id));

        String name = categoryUpdateRequest.name().trim();

        // Validation: unique (case-insensitive), excluding the category itself
        if (categoryRepository.existsByNameIgnoreCaseAndTypeAndIdNot(name, category.getType(), id)) {
            throw ApiException.duplicateResource("Category already exists.");
        }

        category.setName(name);
        return buildResponse(category);
    }

    public CategoryResponse activateCategory(UUID id) {
        return changeActive(id, true);
    }

    public CategoryResponse deactivateCategory(UUID id) {
        return changeActive(id, false);
    }

    // Idempotent: setting the current value again is a no-op
    private CategoryResponse changeActive(UUID id, boolean active) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.resourceNotFound("Category not found: " + id));

        category.setActive(active);
        return buildResponse(category);
    }

    private CategoryResponse buildResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getType(),
                category.isActive()
        );
    }
}
