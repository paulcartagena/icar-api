package com.paulcartagena.icarapi.budget.controller;

import com.paulcartagena.icarapi.budget.dto.BudgetRequest;
import com.paulcartagena.icarapi.budget.dto.BudgetResponse;
import com.paulcartagena.icarapi.budget.service.BudgetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/budgets")
@Tag(name = "Budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PreAuthorize("hasAuthority('FINANCE_READ')")
    @GetMapping
    public List<BudgetResponse> getAllBudgets(@RequestParam(required = false) UUID categoryId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return budgetService.getAllBudgets(categoryId, from, to);
    }

    @PreAuthorize("hasAuthority('FINANCE_READ')")
    @GetMapping("/{id}")
    public BudgetResponse getBudgetById(@PathVariable UUID id) {
        return budgetService.getBudgetById(id);
    }

    @PreAuthorize("hasAuthority('FINANCE_WRITE')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetResponse createBudget(@Valid @RequestBody BudgetRequest budgetRequest,
                                       @AuthenticationPrincipal Jwt jwt) {
        return budgetService.createBudget(budgetRequest, UUID.fromString(jwt.getSubject()));
    }

    @PreAuthorize("hasAuthority('FINANCE_WRITE')")
    @PutMapping("/{id}")
    public BudgetResponse updateBudget(@PathVariable UUID id, @Valid @RequestBody BudgetRequest budgetRequest) {
        return budgetService.updateBudget(id, budgetRequest);
    }

    @PreAuthorize("hasAuthority('FINANCE_WRITE')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBudget(@PathVariable UUID id) {
        budgetService.deleteBudget(id);
    }
}
