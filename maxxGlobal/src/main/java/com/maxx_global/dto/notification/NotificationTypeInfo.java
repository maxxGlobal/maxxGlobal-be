package com.maxx_global.dto.notification;

public record NotificationTypeInfo(
        String name,        // ORDER_CREATED
        String displayName, // "Yeni Sipariş"
        String category,    // "order"
        String icon,        // "shopping-cart"
        String description  // "Yeni sipariş oluşturulduğunda gönderilir"
) {
}