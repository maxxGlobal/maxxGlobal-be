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
    public static NotificationResponse fromRecipient(com.maxx_global.entity.NotificationRecipient recipient,
                                                    com.maxx_global.service.LocalizationService localizationService) {
        com.maxx_global.entity.Notification notification = recipient.getNotification();
        com.maxx_global.entity.AppUser user = recipient.getUser();

        // Null-safe localization: user null ise veya tercih edilmiş dil yoksa default TR kullan
        String localizedTitle = (user != null && localizationService != null)
            ? localizationService.resolveText(user, notification.getTitle(), notification.getTitleEn())
            : (notification.getTitle() != null ? notification.getTitle() : notification.getTitleEn());

        String localizedMessage = (user != null && localizationService != null)
            ? localizationService.resolveText(user, notification.getMessage(), notification.getMessageEn())
            : (notification.getMessage() != null ? notification.getMessage() : notification.getMessageEn());

        return new NotificationResponse(
                notification.getId(),
                localizedTitle != null ? localizedTitle : "",
                localizedMessage != null ? localizedMessage : "",
                notification.getType(),
                notification.getType() != null ? notification.getType().getDisplayName() : "",
                notification.getType() != null ? notification.getType().getCategory() : "",
                recipient.getNotificationStatus(),
                recipient.getNotificationStatus() != null ? recipient.getNotificationStatus().getDisplayName() : "",
                notification.getRelatedEntityId(),
                notification.getRelatedEntityType(),
                recipient.getReadAt(),
                notification.getPriority() != null ? notification.getPriority() : "MEDIUM",
                notification.getIcon(),
                notification.getActionUrl(),
                notification.getData(),
                notification.getCreatedAt(),
                recipient.isRead(),
                calculateTimeAgo(notification.getCreatedAt())
        );
    }

    private static long calculateTimeAgo(LocalDateTime createdAt) {
        if (createdAt == null) return 0;
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toMinutes();
    }
}