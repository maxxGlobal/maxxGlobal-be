package com.maxx_global.dto.cart;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long productId,
        String productName,
        Long productVariantId,
        String variantSku,
        String variantSize,
        Long productPriceId,
        Integer quantity,
        Integer availableStock,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        String currency,
        String imageUrl
) {}
