package com.paulcartagena.icarapi.income.service;

import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.category.enums.CategoryType;
import com.paulcartagena.icarapi.category.repository.CategoryRepository;
import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.income.dto.*;
import com.paulcartagena.icarapi.income.entity.Income;
import com.paulcartagena.icarapi.income.repository.IncomeRepository;
import com.paulcartagena.icarapi.user.repository.AppUserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.paulcartagena.icarapi.income.repository.IncomeSpecifications.dateBetween;
import static com.paulcartagena.icarapi.income.repository.IncomeSpecifications.hasCategory;

@Service
@Transactional
public class IncomeService {

    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;
    private final AppUserRepository appUserRepository;

    public IncomeService(IncomeRepository incomeRepository,
                         CategoryRepository categoryRepository,
                         AppUserRepository appUserRepository) {
        this.incomeRepository = incomeRepository;
        this.categoryRepository = categoryRepository;
        this.appUserRepository = appUserRepository;
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

    @Transactional(readOnly = true)
    public IncomeResponse getIncomeById(UUID id) {
        return buildResponse(findIncome(id));
    }

    public IncomeResponse createIncome(IncomeRequest incomeRequest, UUID registeredById) {
        Category category = findValidIncomeCategory(incomeRequest.categoryId());

        Income income = new Income();
        income.setCategory(category);
        income.setAmount(incomeRequest.amount());
        income.setDescription(normalize(incomeRequest.description()));
        income.setDate(incomeRequest.date());

        // The id comes from our own signed JWT, so a reference (no SELECT) is enough to set the FK
        income.setRegisteredBy(appUserRepository.getReferenceById(registeredById));

        Income savedIncome = incomeRepository.save(income);
        return buildResponse(savedIncome);
    }

    public IncomeResponse updateIncome(UUID id, IncomeRequest incomeRequest) {
        Income income = findIncome(id);

        if (income.isVoided()) {
            throw ApiException.conflict("Voided income cannot be updated.");
        }

        // Its amount must match the donation, so it is managed from /donations
        if (income.getSourceDonation() != null) {
            throw ApiException.conflict("Income linked to a donation cannot be updated.");
        }

        // Only re-validate when the category changes, so incomes whose category
        // was later deactivated can still be corrected
        if (!income.getCategory().getId().equals(incomeRequest.categoryId())) {
            income.setCategory(findValidIncomeCategory(incomeRequest.categoryId()));
        }

        income.setAmount(incomeRequest.amount());
        income.setDescription(normalize(incomeRequest.description()));
        income.setDate(incomeRequest.date());

        return buildResponse(income);
    }

    public IncomeResponse voidIncome(UUID id, IncomeVoidRequest incomeVoidRequest) {
        Income income = findIncome(id);

        // Voiding is final: it cannot be repeated or reverted
        if (income.isVoided()) {
            throw ApiException.conflict("Income is already voided.");
        }

        if (income.getSourceDonation() != null) {
            throw ApiException.conflict("Income linked to a donation must be voided through the donation.");
        }

        income.setVoided(true);
        income.setVoidedAt(LocalDateTime.now());
        income.setVoidReason(incomeVoidRequest.reason().trim());

        return buildResponse(income);
    }

    @Transactional(readOnly = true)
    public IncomeSummaryResponse getSummary(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("'from' must be before or equal to 'to'.");
        }

        List<IncomeCategoryTotal> byCategory = incomeRepository.sumByCategory(from, to);

        BigDecimal total = byCategory.stream()
                .map(IncomeCategoryTotal::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long count = byCategory.stream()
                .mapToLong(IncomeCategoryTotal::count)
                .sum();

        return new IncomeSummaryResponse(from, to, total, count, byCategory);
    }

    private Income findIncome(UUID id) {
        return incomeRepository.findById(id)
                .orElseThrow(() -> ApiException.resourceNotFound("Income not found: " + id));
    }

    // Validation: should be income category
    private Category findValidIncomeCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.resourceNotFound("Category not found: " + categoryId));

        if (category.getType() != CategoryType.INCOME) {
            throw ApiException.badRequest("Category must be of type INCOME.");
        }

        if (!category.isActive()) {
            throw ApiException.badRequest("Category is inactive.");
        }

        return category;
    }

    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
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
