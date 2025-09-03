package com.maxx_global.dto.notification;

import com.maxx_global.enums.NotificationStatus;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record NotificationStatusUpdateRequest(
        @NotNull(message = "Durum gereklidir")
        NotificationStatus notificationStatus,

        List<Long> notificationIds // Toplu güncelleme için
) {
}