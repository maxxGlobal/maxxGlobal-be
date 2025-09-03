package com.maxx_global.enums;

public enum NotificationStatus {
    UNREAD("Okunmamış"),
    READ("Okunmuş"),
    ARCHIVED("Arşivlenmiş");

    private final String displayName;

    NotificationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() { return displayName; }
}