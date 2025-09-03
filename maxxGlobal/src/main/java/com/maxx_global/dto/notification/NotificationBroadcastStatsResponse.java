package com.maxx_global.dto.notification;

import com.maxx_global.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.Map;

public record NotificationBroadcastStatsResponse(
        long totalNotifications,
        long totalUsers,
        Map<NotificationType, Long> notificationsByType,
        Map<String, Long> notificationsByPriority,
        double readRate,
        LocalDateTime periodStart,
        LocalDateTime periodEnd
) {
}