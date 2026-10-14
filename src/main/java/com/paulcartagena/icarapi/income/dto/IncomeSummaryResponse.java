package com.paulcartagena.icarapi.income.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record IncomeSummaryResponse(
        LocalDate from,
        LocalDate to,
        BigDecimal total,
        Long count,
        List<IncomeCategoryTotal> byCategory
) {
}
