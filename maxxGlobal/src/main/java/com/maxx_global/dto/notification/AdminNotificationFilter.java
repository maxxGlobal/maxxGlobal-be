package com.maxx_global.dto.notification;

public record AdminNotificationFilter(
        String type,
        String startDate,
        String endDate,
        String searchTerm,
        String priority
) {}