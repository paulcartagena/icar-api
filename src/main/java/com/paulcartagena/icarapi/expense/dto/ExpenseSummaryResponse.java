package com.paulcartagena.icarapi.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ExpenseSummaryResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal total,
        Long count,
        List<ExpenseCategoryTotal> byCategory
) {
}
