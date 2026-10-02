package com.paulcartagena.icarapi.category.service;

import com.paulcartagena.icarapi.category.dto.CategoryRequest;
import com.paulcartagena.icarapi.category.dto.CategoryResponse;
import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.category.repository.CategoryRepository;
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
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(this::buildResponse)
                .toList();
    }

    public CategoryResponse createCategory(CategoryRequest categoryRequest) {
        // Validation: unique
        if (categoryRepository.existsByNameAndType(categoryRequest.getName(), categoryRequest.getType())) {
            throw new RuntimeException();
        }

        Category category = new Category();
        category.setName(categoryRequest.getName());
        category.setType(categoryRequest.getType());
        category.setActive(true);

        Category savedCategory = categoryRepository.save(category);
        return buildResponse(savedCategory);
    }

    public CategoryResponse updateCategory(UUID id, CategoryRequest categoryRequest) {

        Category category = categoryRepository.findById(id)
                .orElseThrow();

        category.setName(categoryRequest.getName());
        category.setType(categoryRequest.getType());

        Category updatedCategory = categoryRepository.save(category);
        return buildResponse(updatedCategory);
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
