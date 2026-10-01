package com.paulcartagena.icarapi.income.repository;

import com.paulcartagena.icarapi.income.entity.Income;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface IncomeRepository extends JpaRepository<Income, UUID> {

    List<Income> findByDateBetween(LocalDate from, LocalDate to, Sort sort);
    List<Income> findByCategoryId(UUID categoryId, Sort sort);
}
