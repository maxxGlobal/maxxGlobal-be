package com.maxx_global.dto.notification;

import java.time.LocalDateTime;
import java.util.Map;

public record NotificationCleanupPreview(
        LocalDateTime cutoffDate,
        int retentionDays,
        int totalEligible,
        int readNotifications,
        int archivedNotifications,
        Map<String, Integer> userBreakdown,
        long estimatedSpaceSavingKB
) {}
