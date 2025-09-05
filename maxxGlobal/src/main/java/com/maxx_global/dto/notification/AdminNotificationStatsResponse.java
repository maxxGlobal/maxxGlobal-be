package com.maxx_global.dto.notification;

import com.maxx_global.enums.NotificationType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AdminNotificationStatsResponse(
        LocalDateTime periodStart,
        LocalDateTime periodEnd,

        // Genel istatistikler
        int totalNotificationsSent,
        int totalRecipients,
        int totalUniqueUsers,

        // Okunma istatistikleri
        int totalReads,
        double overallReadRate,

        // Tür bazlı istatistikler
        Map<NotificationType, Integer> notificationsByType,
        Map<NotificationType, Double> readRatesByType,

        // Öncelik bazlı istatistikler
        Map<String, Integer> notificationsByPriority,
        Map<String, Double> readRatesByPriority,

        // Broadcast tipi istatistikleri
        Map<String, Integer> notificationsByBroadcastType,

        // En başarılı bildirimler
        List<TopNotificationInfo> topReadNotifications,
        List<TopNotificationInfo> topUnreadNotifications,

        // Trend verileri
        Map<String, Integer> dailyNotificationTrend,
        Map<String, Double> dailyReadRateTrend
) {}