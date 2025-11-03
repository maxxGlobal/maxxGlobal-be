package com.maxx_global.dto.cart;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CartResponse(
        Long id,
        Long dealerId,
        String dealerName,
        LocalDateTime lastActivityAt,
        BigDecimal subtotal,
        String currency,
        Integer totalItems,
        List<CartItemResponse> items
) {}
