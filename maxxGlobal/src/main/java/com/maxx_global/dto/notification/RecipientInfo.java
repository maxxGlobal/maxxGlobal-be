// RecipientInfo.java
package com.maxx_global.dto.notification;

import java.time.LocalDateTime;

public record RecipientInfo(
        Long userId,
        String userName,
        String userEmail,
        boolean isRead,
        LocalDateTime readAt,
        String dealerName
) {}