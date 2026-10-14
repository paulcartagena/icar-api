package com.paulcartagena.icarapi.income.service;

import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.income.dto.IncomeResponse;
import com.paulcartagena.icarapi.income.entity.Income;
import com.paulcartagena.icarapi.income.repository.IncomeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.paulcartagena.icarapi.income.repository.IncomeSpecifications.dateBetween;
import static com.paulcartagena.icarapi.income.repository.IncomeSpecifications.hasCategory;

@Service
@Transactional
public class IncomeService {

    private final IncomeRepository incomeRepository;

    public IncomeService(IncomeRepository incomeRepository) {
        this.incomeRepository = incomeRepository;
    }

    @Transactional(readOnly = true)
    public List<IncomeResponse> getAllIncomes(UUID categoryId,
                                              LocalDate from, LocalDate to) {
        Sort sort = Sort.by(Sort.Direction.DESC, "date");

        // Both bounds or neither
        if ((from == null) != (to == null)) {
            throw ApiException.badRequest("Both 'from' and 'to' are required to filter.");
        }
        if ((from != null) && from.isAfter(to)) {
            throw ApiException.badRequest("'from' must be before or equal to 'to'.");
        }

        // Start unrestricted (all incomes) and add only the filters that were sent
        Specification<Income> spec = Specification.unrestricted();
        if (categoryId != null) {
            spec = spec.and(hasCategory(categoryId));
        }
        if (from != null) {
            spec = spec.and(dateBetween(from, to));
        }

        return incomeRepository.findAll(spec, sort).stream()
                .map(this::buildResponse)
                .toList();
    }

    private IncomeResponse buildResponse(Income income) {
        return new IncomeResponse(
                income.getId(),
                income.getCategory().getId(),
                income.getCategory().getName(),
                income.getSourceDonation() != null ? income.getSourceDonation().getId() : null,
                income.getAmount(),
                income.getDescription(),
                income.getDate(),
                income.isVoided(),
                income.getVoidedAt(),
                income.getVoidReason()
        );
    }
}
