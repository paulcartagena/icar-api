package com.paulcartagena.icarapi.income.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record IncomeCategoryTotal(
        UUID categoryId,
        String categoryName,
        BigDecimal total,
        Long count
) {
}
