package com.maxx_global.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DiscountType {
    PERCENTAGE("Yüzdesel"),
    FIXED_AMOUNT("Sabit Tutar");

    private final String displayName;

    DiscountType(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    @JsonCreator
    public static DiscountType fromValue(String value) {
        // Önce display name ile eşleştir
        for (DiscountType type : DiscountType.values()) {
            if (type.displayName.equals(value)) {
                return type;
            }
        }

        // Sonra enum name ile eşleştir (backward compatibility için)
        for (DiscountType type : DiscountType.values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }

        // Hiçbiri eşleşmezse exception fırlat
        throw new IllegalArgumentException("Unknown DiscountType: " + value);
    }
}