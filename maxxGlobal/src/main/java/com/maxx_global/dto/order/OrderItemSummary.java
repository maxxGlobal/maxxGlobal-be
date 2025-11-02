package com.maxx_global.dto.order;

import java.math.BigDecimal;

public record OrderItemSummary(
        Long productId,
        String productName,
        Long productVariantId,
        String variantSku,
        String variantSize,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        Long productPriceId,
        String primaryImageUrl // ✅ YENİ ALAN EKLENDI - Primary image URL'si
) {}