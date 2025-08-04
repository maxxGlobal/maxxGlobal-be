package com.maxx_global.dto.order;

import java.math.BigDecimal;

public record OrderItemSummary(
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice
) {}
