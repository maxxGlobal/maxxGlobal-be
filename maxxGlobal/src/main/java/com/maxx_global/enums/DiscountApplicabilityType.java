package com.maxx_global.enums;

public enum DiscountApplicabilityType {
    PRODUCT_SPECIFIC,   // Sadece belirli ürünlere
    DEALER_WIDE,        // Bayi geneli (tüm ürünlere)
    MIXED,              // Karışık (bazı ürünlere var, bazılarına yok)
    NOT_APPLICABLE      // Bu bayi için geçerli değil
}