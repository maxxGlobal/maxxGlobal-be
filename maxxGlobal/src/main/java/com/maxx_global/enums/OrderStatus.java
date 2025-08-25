package com.maxx_global.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OrderStatus {
    PENDING("BEKLEMEDE"),
    APPROVED("ONAYLANDI"),
    REJECTED("REDDEDİLDİ"),
    SHIPPED("KARGOLANDI"),
    COMPLETED("TAMAMLANDI"),
    CANCELLED("İPTAL EDİLDİ"),
    EDITED_PENDING_APPROVAL("DÜZENLEME ONAY BEKLIYOR");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return this.name();
    }

    // JSON'dan enum'a dönüştürme için
    @JsonCreator
    public static OrderStatus fromString(String value) {
        for (OrderStatus status : OrderStatus.values()) {
            if (status.name().equalsIgnoreCase(value) ||
                    status.displayName.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown OrderStatus: " + value);
    }

    // Utility method - code'dan display name'e çevirme
    public static String getDisplayNameByCode(String code) {
        try {
            return OrderStatus.valueOf(code.toUpperCase()).getDisplayName();
        } catch (IllegalArgumentException e) {
            return code; // Fallback to original value
        }
    }
}