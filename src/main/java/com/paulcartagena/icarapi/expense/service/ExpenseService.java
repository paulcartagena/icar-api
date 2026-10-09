package com.paulcartagena.icarapi.expense.service;

import com.paulcartagena.icarapi.expense.dto.ExpenseResponse;
import com.paulcartagena.icarapi.expense.entity.Expense;
import com.paulcartagena.icarapi.expense.repository.ExpenseRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    public ExpenseService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
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
