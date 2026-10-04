package com.paulcartagena.icarapi.category.repository;

import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.category.enums.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    // Filter by category type
    List<Category> findByType(CategoryType type);

    // Used to create non-duplicated categories
    boolean existsByNameIgnoreCaseAndType(String name, CategoryType type);

    // Used on update: same name and type, excluding the category being edited
    boolean existsByNameIgnoreCaseAndTypeAndIdNot(String name, CategoryType type, UUID id);
}
