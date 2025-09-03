package com.maxx_global.dto.notification;

public record NotificationPriorityInfo(
        String name,        // "HIGH"
        String displayName, // "Yüksek"
        String color,       // "#fd7e14"
        String icon         // "alert-triangle"
) {
}