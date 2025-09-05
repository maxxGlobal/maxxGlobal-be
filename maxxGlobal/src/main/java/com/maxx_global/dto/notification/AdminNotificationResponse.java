package com.maxx_global.dto.notification;

import com.maxx_global.entity.NotificationGroup;
import com.maxx_global.enums.NotificationType;
import java.time.LocalDateTime;

public record AdminNotificationResponse(
        Long id,
        String title,
        String message,
        NotificationType type,
        String typeDisplayName,
        String priority,
        String icon,
        LocalDateTime createdAt,

        // İstatistik bilgileri
        int totalRecipients,      // Kaç kişiye gönderildi
        int readCount,            // Kaç kişi okudu
        int unreadCount,          // Kaç kişi okumadı
        double readPercentage,    // Okunma yüzdesi

        // Broadcast tipi bilgisi
        String broadcastType,     // "ALL_USERS", "ROLE_BASED", "DEALER_SPECIFIC", "SPECIFIC_USERS"
        String targetInfo,        // "Tüm Kullanıcılar", "ADMIN Rolü", "Bayi: ABC Corp", "5 Kullanıcı"

        // Son okunma tarihi
        LocalDateTime lastReadAt
) {

    public static AdminNotificationResponse from(NotificationGroup group) {
        return new AdminNotificationResponse(
                group.getId(),
                group.getTitle(),
                group.getMessage(),
                group.getType(),
                group.getType().getDisplayName(),
                group.getPriority(),
                group.getIcon(),
                group.getCreatedAt(),
                group.getTotalRecipients(),
                group.getReadCount(),
                group.getUnreadCount(),
                group.getReadPercentage(),
                group.getBroadcastType(),
                group.getTargetInfo(),
                group.getLastReadAt()
        );
    }
}