package com.maxx_global.dto.discount;

import java.math.BigDecimal;
import java.util.Map;

public class DiscountItemResult {
    private final Map<Long, BigDecimal> itemDiscounts;
    private final BigDecimal totalDiscount;

    public DiscountItemResult(Map<Long, BigDecimal> itemDiscounts, BigDecimal totalDiscount) {
        this.itemDiscounts = itemDiscounts;
        this.totalDiscount = totalDiscount;
    }

    public Map<Long, BigDecimal> itemDiscounts() { return itemDiscounts; }
    public BigDecimal totalDiscount() { return totalDiscount; }
}