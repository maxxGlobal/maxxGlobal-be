package com.maxx_global.dto.notification;

import java.time.LocalDateTime;

public record NotificationCleanupStatus(
        boolean enabled,
        int retentionDays,
        LocalDateTime lastRun,
        int totalNotifications,
        int readNotifications,
        int eligibleForCleanup,
        String status
) {}