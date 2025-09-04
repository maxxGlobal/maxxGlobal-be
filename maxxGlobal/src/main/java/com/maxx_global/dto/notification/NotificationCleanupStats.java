package com.maxx_global.dto.notification;

import java.time.LocalDateTime;

public record NotificationCleanupStats(
        int totalCleaned,
        LocalDateTime weekStart,
        LocalDateTime weekEnd,
        double averagePerDay
) {}