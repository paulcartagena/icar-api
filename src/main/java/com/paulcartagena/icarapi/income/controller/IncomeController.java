package com.paulcartagena.icarapi.income.controller;

import com.paulcartagena.icarapi.income.dto.IncomeRequest;
import com.paulcartagena.icarapi.income.dto.IncomeResponse;
import com.paulcartagena.icarapi.income.dto.IncomeSummaryResponse;
import com.paulcartagena.icarapi.income.dto.IncomeVoidRequest;
import com.paulcartagena.icarapi.income.service.IncomeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/incomes")
@Tag(name = "Incomes")
public class IncomeController {

    private final IncomeService incomeService;

    public IncomeController(IncomeService incomeService) {
        this.incomeService = incomeService;
    }

    @GetMapping
    public List<IncomeResponse> getAllIncomes(@RequestParam(required = false) UUID categoryId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return incomeService.getAllIncomes(categoryId, from, to);
    }

    @GetMapping("/{id}")
    public IncomeResponse getIncomeById(@PathVariable UUID id) {
        return incomeService.getIncomeById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncomeResponse createIncome(@Valid @RequestBody IncomeRequest incomeRequest) {
        return incomeService.createIncome(incomeRequest);
    }

    @PutMapping("/{id}")
    public IncomeResponse updateIncome(@PathVariable UUID id, @Valid @RequestBody IncomeRequest incomeRequest) {
        return incomeService.updateIncome(id, incomeRequest);
    }

    @PatchMapping("/{id}/void")
    public IncomeResponse voidIncome(@PathVariable UUID id,
                                     @Valid @RequestBody IncomeVoidRequest incomeVoidRequest) {
        return incomeService.voidIncome(id, incomeVoidRequest);
    }

    @GetMapping("/summary")
    public IncomeSummaryResponse getSummary(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return incomeService.getSummary(from, to);
    }
}
