package com.paulcartagena.icarapi.budget.service;

import com.paulcartagena.icarapi.budget.dto.BudgetRequest;
import com.paulcartagena.icarapi.budget.dto.BudgetResponse;
import com.paulcartagena.icarapi.budget.entity.Budget;
import com.paulcartagena.icarapi.budget.repository.BudgetRepository;
import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.category.enums.CategoryType;
import com.paulcartagena.icarapi.category.repository.CategoryRepository;
import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.expense.repository.ExpenseRepository;
import com.paulcartagena.icarapi.income.repository.IncomeRepository;
import com.paulcartagena.icarapi.user.repository.AppUserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.paulcartagena.icarapi.budget.repository.BudgetSpecifications.hasCategory;
import static com.paulcartagena.icarapi.budget.repository.BudgetSpecifications.overlaps;

@Service
@Transactional
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final AppUserRepository appUserRepository;

    public BudgetService(BudgetRepository budgetRepository,
                         CategoryRepository categoryRepository,
                         ExpenseRepository expenseRepository,
                         IncomeRepository incomeRepository,
                         AppUserRepository appUserRepository) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
        this.incomeRepository = incomeRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> getAllBudgets(UUID categoryId, LocalDate from, LocalDate to) {
        Sort sort = Sort.by(Sort.Direction.DESC, "periodStart");

        // Both bounds or neither
        if ((from == null) != (to == null)) {
            throw ApiException.badRequest("Both 'from' and 'to' are required to filter.");
        }
        if ((from != null) && from.isAfter(to)) {
            throw ApiException.badRequest("'from' must be before or equal to 'to'.");
        }

        Specification<Budget> spec = Specification.unrestricted();
        if (categoryId != null) {
            spec = spec.and(hasCategory(categoryId));
        }
        if (from != null) {
            spec = spec.and(overlaps(from, to));
        }

        return budgetRepository.findAll(spec, sort).stream()
                .map(this::buildResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BudgetResponse getBudgetById(UUID id) {
        return buildResponse(findBudget(id));
    }

    public BudgetResponse createBudget(BudgetRequest budgetRequest, UUID createdById) {
        validatePeriod(budgetRequest);
        Category category = findValidCategory(budgetRequest.categoryId());
        checkNoOverlap(category.getId(), budgetRequest, null);

        Budget budget = new Budget();
        budget.setCategory(category);
        budget.setPeriodStart(budgetRequest.periodStart());
        budget.setPeriodEnd(budgetRequest.periodEnd());
        budget.setPlannedAmount(budgetRequest.plannedAmount());

        // The id comes from our own signed JWT, so a reference (no SELECT) is enough to set the FK
        budget.setCreatedBy(appUserRepository.getReferenceById(createdById));

        Budget savedBudget = budgetRepository.save(budget);
        return buildResponse(savedBudget);
    }

    public BudgetResponse updateBudget(UUID id, BudgetRequest budgetRequest) {
        Budget budget = findBudget(id);
        validatePeriod(budgetRequest);

        // Only re-validate when the category changes, so budgets whose category
        // was later deactivated can still be corrected
        if (!budget.getCategory().getId().equals(budgetRequest.categoryId())) {
            budget.setCategory(findValidCategory(budgetRequest.categoryId()));
        }
        checkNoOverlap(budget.getCategory().getId(), budgetRequest, budget.getId());

        budget.setPeriodStart(budgetRequest.periodStart());
        budget.setPeriodEnd(budgetRequest.periodEnd());
        budget.setPlannedAmount(budgetRequest.plannedAmount());

        return buildResponse(budget);
    }

    // A budget is a plan, not a money movement, so it can be deleted
    public void deleteBudget(UUID id) {
        budgetRepository.delete(findBudget(id));
    }

    private Budget findBudget(UUID id) {
        return budgetRepository.findById(id)
                .orElseThrow(() -> ApiException.resourceNotFound("Budget not found: " + id));
    }

    private void validatePeriod(BudgetRequest budgetRequest) {
        if (budgetRequest.periodStart().isAfter(budgetRequest.periodEnd())) {
            throw ApiException.badRequest("'periodStart' must be before or equal to 'periodEnd'.");
        }
    }

    // Validation: category must exist and be active (any type: EXPENSE or INCOME)
    private Category findValidCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.resourceNotFound("Category not found: " + categoryId));

        if (!category.isActive()) {
            throw ApiException.badRequest("Category is inactive.");
        }

        return category;
    }

    // One budget per category and day, so planned vs actual is never ambiguous.
    // excludeId skips the budget being updated.
    private void checkNoOverlap(UUID categoryId, BudgetRequest budgetRequest, UUID excludeId) {
        boolean overlaps = budgetRepository
                .findOverlapping(categoryId, budgetRequest.periodStart(), budgetRequest.periodEnd())
                .stream()
                .anyMatch(other -> !other.getId().equals(excludeId));

        if (overlaps) {
            throw ApiException.conflict("Category already has a budget overlapping this period.");
        }
    }

    private BigDecimal actualAmount(Budget budget) {
        UUID categoryId = budget.getCategory().getId();
        LocalDate from = budget.getPeriodStart();
        LocalDate to = budget.getPeriodEnd();

        return budget.getCategory().getType() == CategoryType.EXPENSE
                ? expenseRepository.sumByCategoryBetween(categoryId, from, to)
                : incomeRepository.sumByCategoryBetween(categoryId, from, to);
    }

    private BudgetResponse buildResponse(Budget budget) {
        BigDecimal actual = actualAmount(budget);

        return new BudgetResponse(
                budget.getId(),
                budget.getCategory().getId(),
                budget.getCategory().getName(),
                budget.getCategory().getType(),
                budget.getPeriodStart(),
                budget.getPeriodEnd(),
                budget.getPlannedAmount(),
                actual,
                budget.getPlannedAmount().subtract(actual)
        );
    }
}
