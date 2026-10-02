package com.paulcartagena.icarapi.category.repository;

import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.category.enums.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // Filter by category type
    List<Category> findByType(CategoryType type);

    // Used to create non-duplicated categories
    boolean existsByNameAndType(String name, CategoryType type);
}
