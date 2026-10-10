package com.paulcartagena.icarapi.expense.service;

import com.paulcartagena.icarapi.category.entity.Category;
import com.paulcartagena.icarapi.category.enums.CategoryType;
import com.paulcartagena.icarapi.category.repository.CategoryRepository;
import com.paulcartagena.icarapi.exception.ApiException;
import com.paulcartagena.icarapi.expense.dto.ExpenseRequest;
import com.paulcartagena.icarapi.expense.dto.ExpenseResponse;
import com.paulcartagena.icarapi.expense.entity.Expense;
import com.paulcartagena.icarapi.expense.repository.ExpenseRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;

    public ExpenseService(ExpenseRepository expenseRepository,
                          CategoryRepository categoryRepository) {
        this.expenseRepository = expenseRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getAllExpenses() {
        Sort sort = Sort.by(Sort.Direction.DESC, "date");
        List<Expense> expenses;

        expenses = expenseRepository.findAll(sort);

        return expenses.stream()
                .map(this::buildResponse)
                .toList();
    }

    public ExpenseResponse createExpense(ExpenseRequest expenseRequest) {
        Category category = findValidExpenseCategory(expenseRequest.categoryId());

        Expense expense = new Expense();
        expense.setCategory(category);
        expense.setAmount(expenseRequest.amount());
        expense.setDescription(normalize(expenseRequest.description()));
        expense.setDate(expenseRequest.date());
        expense.setReceiptUrl(normalize(expenseRequest.receiptUrl()));

        Expense savedExpense = expenseRepository.save(expense);
        return buildResponse(savedExpense);
    }

    // Validation: should be expense category
    private Category findValidExpenseCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.resourceNotFound("Category not found: " + categoryId));

        if (category.getType() != CategoryType.EXPENSE) {
            throw ApiException.badRequest("Category must be of type EXPENSE.");
        }

        if (!category.isActive()) {
            throw ApiException.badRequest("Category is inactive.");
        }

        return category;
    }

    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private ExpenseResponse buildResponse(Expense expense) {
        return new ExpenseResponse(
                expense.getId(),
                expense.getCategory().getId(),
                expense.getCategory().getName(),
                expense.getAmount(),
                expense.getDescription(),
                expense.getDate(),
                expense.getReceiptUrl()
        );
    }
}
