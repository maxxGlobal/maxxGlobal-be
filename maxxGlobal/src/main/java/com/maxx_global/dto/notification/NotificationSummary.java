package com.maxx_global.dto.notification;

public record NotificationSummary(
        long totalCount,
        long unreadCount,
        long readCount,
        long archivedCount,
        long todayCount,
        long thisWeekCount,
        long highPriorityUnreadCount
) {
}