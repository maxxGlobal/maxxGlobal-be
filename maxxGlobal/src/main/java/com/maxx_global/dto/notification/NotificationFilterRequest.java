package com.maxx_global.dto.notification;

import com.maxx_global.enums.NotificationStatus;
import com.maxx_global.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationFilterRequest(
        NotificationStatus notificationStatus,
        NotificationType type,
        String priority,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String category, // type.category değeri
        boolean unreadOnly
) {
}