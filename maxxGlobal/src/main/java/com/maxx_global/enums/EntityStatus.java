package com.maxx_global.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum EntityStatus {
    ACTIVE("AKTİF"),
    INACTIVE("PASİF"),
    DELETED("SİLİNDİ");

    private final String displayName;

    EntityStatus(String displayName) {
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
    public static EntityStatus fromString(String value) {
        for (EntityStatus status : EntityStatus.values()) {
            if (status.name().equalsIgnoreCase(value) ||
                    status.displayName.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown EntityStatus: " + value);
    }

    // Utility method - code'dan display name'e çevirme
    public static String getDisplayNameByCode(String code) {
        try {
            return EntityStatus.valueOf(code.toUpperCase()).getDisplayName();
        } catch (IllegalArgumentException e) {
            return code; // Fallback to original value
        }
    }

}