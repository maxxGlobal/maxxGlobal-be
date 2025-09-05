// TopNotificationInfo.java
package com.maxx_global.dto.notification;

import java.time.LocalDateTime;

public record TopNotificationInfo(
        Long id,
        String title,
        String type,
        int totalRecipients,
        int readCount,
        double readPercentage,
        LocalDateTime createdAt
) {}
