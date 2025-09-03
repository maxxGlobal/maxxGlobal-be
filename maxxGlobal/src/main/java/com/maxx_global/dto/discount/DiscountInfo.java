// DiscountInfo.java - Basic factory method ile basit versiyon

package com.maxx_global.dto.discount;

import java.math.BigDecimal;

public record DiscountInfo(
        Long discountId,
        String discountName,
        String discountType,
        BigDecimal discountValue,
        BigDecimal calculatedAmount
) {

    // Basit factory method
    public static DiscountInfo basic(Long id, String name, String type, BigDecimal value, BigDecimal calculated) {
        return new DiscountInfo(id, name, type, value, calculated);
    }
}