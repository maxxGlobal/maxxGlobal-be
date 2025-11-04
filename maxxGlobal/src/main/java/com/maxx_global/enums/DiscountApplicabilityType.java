package com.maxx_global.enums;

public enum DiscountApplicabilityType {
    PRODUCT_SPECIFIC,   // Sadece belirli varyantlara
    DEALER_WIDE,        // Bayi geneli (tüm varyantlara)
    MIXED,              // Karışık (bazı varyantlara var, bazılarına yok)
    NOT_APPLICABLE      // Bu bayi için geçerli değil
}