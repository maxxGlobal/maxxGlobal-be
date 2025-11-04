package com.maxx_global.dto.discount;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class ProductBasedDiscountCalculation {
    private final BigDecimal totalDiscountAmount;
    private final String discountDescription;
    private final Map<Long, BigDecimal> itemDiscountAmounts;

    public ProductBasedDiscountCalculation(BigDecimal totalDiscountAmount,
                                           String discountDescription,
                                           Map<Long, BigDecimal> itemDiscountAmounts) {
        this.totalDiscountAmount = totalDiscountAmount;
        this.discountDescription = discountDescription;
        this.itemDiscountAmounts = itemDiscountAmounts != null ? itemDiscountAmounts : new HashMap<>();
    }

    public BigDecimal totalDiscountAmount() { return totalDiscountAmount; }
    public String discountDescription() { return discountDescription; }

    public BigDecimal getItemDiscountAmount(Long variantId) {
        return itemDiscountAmounts.getOrDefault(variantId, BigDecimal.ZERO);
    }
}