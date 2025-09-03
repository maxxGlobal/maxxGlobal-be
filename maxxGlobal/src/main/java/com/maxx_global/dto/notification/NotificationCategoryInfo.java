package com.maxx_global.dto.notification;

import java.util.List;

public record NotificationCategoryInfo(
        String name,           // "order"
        String displayName,    // "Sipariş Bildirimleri"
        String icon,          // "shopping-cart"
        int typeCount,        // 7 (kaç tür var bu kategoride)
        List<String> types    // ["ORDER_CREATED", "ORDER_APPROVED", ...]
) {
}