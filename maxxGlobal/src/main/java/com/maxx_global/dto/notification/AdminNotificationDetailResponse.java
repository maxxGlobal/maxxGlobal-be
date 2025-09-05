// AdminNotificationDetailResponse.java
package com.maxx_global.dto.notification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AdminNotificationDetailResponse(
        Long id,
        String title,
        String message,
        String type,
        String priority,
        String icon,
        String actionUrl,
        String data,
        LocalDateTime createdAt,

        // Detaylı istatistikler
        int totalRecipients,
        int readCount,
        int unreadCount,
        double readPercentage,

        // Zaman bazlı istatistikler
        Map<String, Integer> readStatsByHour,  // Saatlik okuma dağılımı
        Map<String, Integer> readStatsByDay,   // Günlük okuma dağılımı

        // Hedef kitle bilgisi
        String broadcastType,
        String targetInfo,
        List<RecipientInfo> sampleRecipients,  // İlk 10 alıcı örneği

        // Timing bilgileri
        LocalDateTime firstReadAt,
        LocalDateTime lastReadAt,
        long averageReadTimeMinutes
) {}