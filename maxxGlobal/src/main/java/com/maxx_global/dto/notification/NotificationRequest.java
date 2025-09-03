package com.maxx_global.dto.notification;

import com.maxx_global.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotificationRequest(
        @NotNull(message = "Kullanıcı ID'si gereklidir")
        Long userId,

        @NotBlank(message = "Başlık gereklidir")
        @Size(max = 200, message = "Başlık 200 karakterden uzun olamaz")
        String title,

        @NotBlank(message = "Mesaj gereklidir")
        @Size(max = 1000, message = "Mesaj 1000 karakterden uzun olamaz")
        String message,

        @NotNull(message = "Bildirim tipi gereklidir")
        NotificationType type,

        Long relatedEntityId,

        String relatedEntityType,

        String priority, // LOW, MEDIUM, HIGH, URGENT

        String icon,

        String actionUrl,

        String data // JSON string
) {
}