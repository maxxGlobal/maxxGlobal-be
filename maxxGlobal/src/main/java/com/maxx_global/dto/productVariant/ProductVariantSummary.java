package com.maxx_global.dto.productVariant;

public record ProductVariantSummary(
        Long id,
        String sku,
        String size,
        Long productId,
        String productName,
        String displayName,
        Boolean isDefault,
        Integer stockQuantity
) {}
