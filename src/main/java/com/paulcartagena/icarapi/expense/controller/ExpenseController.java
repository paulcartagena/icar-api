package com.paulcartagena.icarapi.expense.controller;

import com.paulcartagena.icarapi.expense.dto.ExpenseRequest;
import com.paulcartagena.icarapi.expense.dto.ExpenseResponse;
import com.paulcartagena.icarapi.expense.service.ExpenseService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/expenses")
@Tag(name = "Expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    public List<ExpenseResponse> getAllExpenses(@RequestParam(required = false) UUID categoryId,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return expenseService.getAllExpenses(categoryId, from, to);
    }

    @GetMapping("/{id}")
    public ExpenseResponse getExpenseById(@PathVariable UUID id) {
        return expenseService.getExpenseById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse createExpense(@Valid @RequestBody ExpenseRequest expenseRequest) {
        return expenseService.createExpense(expenseRequest);
    }

    @PutMapping("/{id}")
    public ExpenseResponse updateExpense(@PathVariable UUID id, @Valid @RequestBody ExpenseRequest expenseRequest) {
        return expenseService.updateExpense(id, expenseRequest);
    }
}
