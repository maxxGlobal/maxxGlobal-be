package com.maxx_global.dto.discount;

import java.math.BigDecimal;

public record DiscountInfo(
        Long discountId,
        String discountName,
        String discountType,
        BigDecimal discountValue,
        BigDecimal calculatedAmount
) {}