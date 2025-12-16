package com.maxx_global.dto.notification;

import com.maxx_global.enums.NotificationStatus;
import com.maxx_global.enums.NotificationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public record NotificationResponse(
        Long id,
        String title,
        String message,
        NotificationType type,
        String typeDisplayName,
        String typeCategory,
        NotificationStatus notificationStatus,
        String statusDisplayName,
        Long relatedEntityId,
        String relatedEntityType,
        LocalDateTime readAt,
        String priority,
        String icon,
        String actionUrl,
        String data,
        LocalDateTime createdAt,
        boolean isRead,
        long timeAgo // Frontend için "2 dakika önce" gibi gösterim
) {
    public static NotificationResponse fromRecipient(com.maxx_global.entity.NotificationRecipient recipient,
                                                    com.maxx_global.service.LocalizationService localizationService) {
        com.maxx_global.entity.Notification notification = recipient.getNotification();
        com.maxx_global.entity.AppUser user = recipient.getUser();

        Logger logger = Logger.getLogger(NotificationResponse.class.getName());
        ObjectMapper objectMapper = new ObjectMapper();

        // Null-safe localization: user null ise veya tercih edilmiş dil yoksa default TR kullan
        String localizedTitle = (user != null && localizationService != null)
            ? localizationService.resolveText(user, notification.getTitle(), notification.getTitleEn())
            : (notification.getTitle() != null ? notification.getTitle() : notification.getTitleEn());

        String localizedMessage = (user != null && localizationService != null)
            ? localizationService.resolveText(user, notification.getMessage(), notification.getMessageEn())
            : (notification.getMessage() != null ? notification.getMessage() : notification.getMessageEn());

        if (notification.getType() == NotificationType.PROMOTION
                && localizedMessage != null
                && localizedMessage.contains("{0}")
                && notification.getData() != null
                && localizationService != null) {
            try {
                Map<String, Object> payload = objectMapper.readValue(notification.getData(), Map.class);
                Locale locale = localizationService.getLanguageForUser(user).toLocale();
                localizedMessage = formatDiscountMessage(localizedMessage, localizedTitle, payload, locale, localizationService);
            } catch (JsonProcessingException e) {
                logger.warning("Failed to parse notification data for formatting: " + e.getMessage());
            }
        }

        return new NotificationResponse(
                notification.getId(),
                localizedTitle != null ? localizedTitle : "",
                localizedMessage != null ? localizedMessage : "",
                notification.getType(),
                notification.getType() != null ? notification.getType().getDisplayName() : "",
                notification.getType() != null ? notification.getType().getCategory() : "",
                recipient.getNotificationStatus(),
                recipient.getNotificationStatus() != null ? recipient.getNotificationStatus().getDisplayName() : "",
                notification.getRelatedEntityId(),
                notification.getRelatedEntityType(),
                recipient.getReadAt(),
                notification.getPriority() != null ? notification.getPriority() : "MEDIUM",
                notification.getIcon(),
                notification.getActionUrl(),
                notification.getData(),
                notification.getCreatedAt(),
                recipient.isRead(),
                calculateTimeAgo(notification.getCreatedAt())
        );
    }

    private static long calculateTimeAgo(LocalDateTime createdAt) {
        if (createdAt == null) return 0;
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toMinutes();
    }

    private static String formatDiscountMessage(String template,
                                                String localizedTitle,
                                                Map<String, Object> payload,
                                                Locale locale,
                                                com.maxx_global.service.LocalizationService localizationService) {
        Object[] args = new Object[5];
        args[0] = Optional.ofNullable(payload.get("discountName")).orElse("");
        args[1] = resolveDiscountAction(localizedTitle, locale, localizationService);
        args[2] = buildDiscountValue(payload, locale);
        args[3] = ""; // Ürün bilgisi payload'da olmadığı için boş bırakıyoruz
        args[4] = formatEndDate(payload.get("endDate"), locale);

        return java.text.MessageFormat.format(template, args);
    }

    private static String resolveDiscountAction(String localizedTitle,
                                                Locale locale,
                                                com.maxx_global.service.LocalizationService localizationService) {
        if (localizedTitle == null || localizationService == null) {
            return "";
        }

        String createdTitle = localizationService.getMessage("discount.created.title", locale);
        String updatedTitle = localizationService.getMessage("discount.updated.title", locale);

        if (localizedTitle.contains(createdTitle)) {
            return localizationService.getMessage("discount.created.action", locale);
        }
        if (localizedTitle.contains(updatedTitle)) {
            return localizationService.getMessage("discount.updated.action", locale);
        }
        return "";
    }

    private static String buildDiscountValue(Map<String, Object> payload, Locale locale) {
        if (payload == null || !payload.containsKey("discountType")) {
            return "";
        }

        String type = String.valueOf(payload.get("discountType"));
        Number value = payload.get("discountValue") instanceof Number ? (Number) payload.get("discountValue") : null;
        boolean isEnglish = locale != null && Locale.ENGLISH.getLanguage().equals(locale.getLanguage());

        if ("PERCENTAGE".equalsIgnoreCase(type) && value != null) {
            return "%" + value.intValue() + (isEnglish ? " off" : " indirim");
        }

        if (value != null) {
            return value + (isEnglish ? " discount" : " indirim");
        }

        return "";
    }

    private static String formatEndDate(Object endDate, Locale locale) {
        if (endDate == null) {
            return "";
        }

        try {
            LocalDateTime dateTime = LocalDateTime.parse(String.valueOf(endDate));
            String pattern = (locale != null && Locale.ENGLISH.getLanguage().equals(locale.getLanguage()))
                    ? "MM.dd.yyyy"
                    : "dd.MM.yyyy";
            return dateTime.format(DateTimeFormatter.ofPattern(pattern));
        } catch (Exception e) {
            return String.valueOf(endDate);
        }
    }
}