package com.maxx_global.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StockTransactionStatus {
    PENDING("BEKLEMEDE"),
    APPROVED("ONAYLANDI"),
    REJECTED("REDDEDİLDİ"),
    COMPLETED("TAMAMLANDI"),
    CANCELLED("İPTAL EDİLDİ");

    private final String displayName;

    StockTransactionStatus(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return this.name();
    }

    @JsonCreator
    public static StockTransactionStatus fromString(String value) {
        for (StockTransactionStatus status : StockTransactionStatus.values()) {
            if (status.name().equalsIgnoreCase(value) ||
                    status.displayName.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown StockTransactionStatus: " + value);
    }
}