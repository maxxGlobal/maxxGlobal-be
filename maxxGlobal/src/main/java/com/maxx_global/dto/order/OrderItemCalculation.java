package com.maxx_global.dto.order;

import java.math.BigDecimal;

public record OrderItemCalculation(
        Long productId,
        String productName,
        String productCode,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        Boolean inStock,
        Integer availableStock,
        BigDecimal discountAmount,
        String stockStatus
) {}