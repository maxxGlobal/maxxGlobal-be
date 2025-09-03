package com.maxx_global.dto.notification;

import com.maxx_global.enums.NotificationStatus;
import com.maxx_global.enums.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String title,
        String message,
        NotificationType type,
        String typeDisplayName,
        String typeCategory,
        NotificationStatus notificationStatus,
        String statusDisplayName,
        Long relatedEntityId,
        String relatedEntityType,
        LocalDateTime readAt,
        String priority,
        String icon,
        String actionUrl,
        String data,
        LocalDateTime createdAt,
        boolean isRead,
        long timeAgo // Frontend için "2 dakika önce" gibi gösterim
) {
    // Factory method
    public static NotificationResponse from(com.maxx_global.entity.Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                notification.getType().getDisplayName(),
                notification.getType().getCategory(),
                notification.getNotificationStatus(),
                notification.getNotificationStatus().getDisplayName(),
                notification.getRelatedEntityId(),
                notification.getRelatedEntityType(),
                notification.getReadAt(),
                notification.getPriority(),
                notification.getIcon(),
                notification.getActionUrl(),
                notification.getData(),
                notification.getCreatedAt(),
                notification.isRead(),
                calculateTimeAgo(notification.getCreatedAt())
        );
    }

    private static long calculateTimeAgo(LocalDateTime createdAt) {
        if (createdAt == null) return 0;
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toMinutes();
    }
}