package com.paulcartagena.icarapi.expense.controller;

import com.paulcartagena.icarapi.expense.dto.ExpenseRequest;
import com.paulcartagena.icarapi.expense.dto.ExpenseResponse;
import com.paulcartagena.icarapi.expense.service.ExpenseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/expenses")
@Tag(name = "Expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    public List<ExpenseResponse> getAllExpenses() {
        return expenseService.getAllExpenses();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse createExpense(@Valid @RequestBody ExpenseRequest expenseRequest) {
        return expenseService.createExpense(expenseRequest);
    }
}
