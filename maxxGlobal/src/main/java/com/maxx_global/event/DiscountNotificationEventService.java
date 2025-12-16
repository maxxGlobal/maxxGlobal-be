package com.maxx_global.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maxx_global.dto.notification.NotificationBroadcastRequest;
import com.maxx_global.dto.notification.NotificationRequest;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.Discount;
import com.maxx_global.entity.ProductVariant;
import com.maxx_global.enums.DiscountType;
import com.maxx_global.enums.Language;
import com.maxx_global.enums.NotificationType;
import com.maxx_global.service.LocalizationService;
import com.maxx_global.service.NotificationService;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

@Service
public class DiscountNotificationEventService {

    private static final Logger logger = Logger.getLogger(DiscountNotificationEventService.class.getName());

    private final NotificationService notificationService;
    private final LocalizationService localizationService;
    private final ObjectMapper objectMapper;

    public DiscountNotificationEventService(NotificationService notificationService,
                                            LocalizationService localizationService,
                                            ObjectMapper objectMapper) {
        this.notificationService = notificationService;
        this.localizationService = localizationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Yeni indirim oluşturulduğunda bildirim gönder
     */
    public void sendDiscountCreatedNotification(Discount discount) {
        logger.info("Sending discount created notification for: " + discount.getName());

        try {
            // 1. Dealer bazlı indirim mi kontrol et
            if (hasSpecificDealers(discount)) {
                sendDealerSpecificDiscountNotification(discount, "CREATED");
            }
            // 2. Varyant bazlı veya genel indirim ise tüm kullanıcılara gönder
            else if (hasSpecificVariants(discount) || isGeneralDiscount(discount)) {
                sendGeneralDiscountNotification(discount, "CREATED");
            }

            logger.info("Discount created notification sent successfully");

        } catch (Exception e) {
            logger.severe("Error sending discount created notification: " + e.getMessage());
        }
    }

    /**
     * İndirim güncellendiğinde bildirim gönder
     */
    public void sendDiscountUpdatedNotification(Discount discount, boolean isActivationChanged, boolean isDateChanged) {
        logger.info("Sending discount updated notification for: " + discount.getName());

        try {
            // Sadece önemli değişikliklerde bildirim gönder
            if (isActivationChanged || isDateChanged) {
                if (hasSpecificDealers(discount)) {
                    sendDealerSpecificDiscountNotification(discount, "UPDATED");
                } else if (hasSpecificVariants(discount) || isGeneralDiscount(discount)) {
                    sendGeneralDiscountNotification(discount, "UPDATED");
                }
            }

            logger.info("Discount updated notification sent successfully");

        } catch (Exception e) {
            logger.severe("Error sending discount updated notification: " + e.getMessage());
        }
    }

    /**
     * İndirim süresi dolduğunda bildirim gönder
     */
    public void sendDiscountExpiredNotification(Discount discount) {
        logger.info("Sending discount expired notification for: " + discount.getName());

        try {
            if (hasSpecificDealers(discount)) {
                sendDealerSpecificDiscountExpiredNotification(discount);
            } else if (hasSpecificVariants(discount) || isGeneralDiscount(discount)) {
                sendGeneralDiscountExpiredNotification(discount);
            }

            logger.info("Discount expired notification sent successfully");

        } catch (Exception e) {
            logger.severe("Error sending discount expired notification: " + e.getMessage());
        }
    }

    /**
     * İndirim süresi yakında dolacağında bildirim gönder
     */
    public void sendDiscountSoonExpiringNotification(Discount discount, int daysUntilExpiration) {
        logger.info("Sending discount soon expiring notification for: " + discount.getName() +
                " (expires in " + daysUntilExpiration + " days)");

        try {
            if (hasSpecificDealers(discount)) {
                sendDealerSpecificDiscountSoonExpiringNotification(discount, daysUntilExpiration);
            } else if (hasSpecificVariants(discount) || isGeneralDiscount(discount)) {
                sendGeneralDiscountSoonExpiringNotification(discount, daysUntilExpiration);
            }

            logger.info("Discount soon expiring notification sent successfully");

        } catch (Exception e) {
            logger.severe("Error sending discount soon expiring notification: " + e.getMessage());
        }
    }

    // ==================== DEALER SPECIFIC NOTIFICATIONS ====================

    private void sendDealerSpecificDiscountNotification(Discount discount, String action) {
        for (Dealer dealer : discount.getApplicableDealers()) {
            try {
                String title = createDealerDiscountTitle(discount, action, Language.TR);
                String titleEn = createDealerDiscountTitle(discount, action, Language.EN);
                String message = createDealerDiscountMessage(discount, action, Language.TR);
                String messageEn = createDealerDiscountMessage(discount, action, Language.EN);

                NotificationRequest request = new NotificationRequest(
                        dealer.getId(),
                        title,
                        titleEn,
                        message,
                        messageEn,
                        NotificationType.PROMOTION,
                        discount.getId(),
                        "DISCOUNT",
                        "MEDIUM",
                        "percent",
                        "/discounts/" + discount.getId(),
                        createDiscountData(discount)
                );

                notificationService.createNotification(request);
                logger.info("Dealer-specific discount notification sent to dealer: " + dealer.getId());

            } catch (Exception e) {
                logger.warning("Failed to send dealer notification to dealer " + dealer.getId() + ": " + e.getMessage());
            }
        }
    }

    private void sendDealerSpecificDiscountExpiredNotification(Discount discount) {
        for (Dealer dealer : discount.getApplicableDealers()) {
            try {
                NotificationRequest request = new NotificationRequest(
                        dealer.getId(),
                        getLocalizedMessage("discount.expired.title", Language.TR),
                        getLocalizedMessage("discount.expired.title", Language.EN),
                        String.format(getLocalizedMessage("discount.expired.dealer.message", Language.TR),
                                getLocalizedDiscountName(discount)),
                        String.format(getLocalizedMessage("discount.expired.dealer.message", Language.EN),
                                getLocalizedDiscountName(discount)),
                        NotificationType.PROMOTION,
                        discount.getId(),
                        "DISCOUNT",
                        "LOW",
                        "clock",
                        "/discounts",
                        createDiscountData(discount)
                );

                notificationService.createNotification(request);

            } catch (Exception e) {
                logger.warning("Failed to send dealer expired notification to dealer " + dealer.getId() + ": " + e.getMessage());
            }
        }
    }

    private void sendDealerSpecificDiscountSoonExpiringNotification(Discount discount, int daysUntilExpiration) {
        for (Dealer dealer : discount.getApplicableDealers()) {
            try {
                NotificationRequest request = new NotificationRequest(
                        dealer.getId(),
                        getLocalizedMessage("discount.expiring.title", Language.TR),
                        getLocalizedMessage("discount.expiring.title", Language.EN),
                        String.format(getLocalizedMessage("discount.expiring.dealer.message", Language.TR),
                                getLocalizedDiscountName(discount), daysUntilExpiration),
                        String.format(getLocalizedMessage("discount.expiring.dealer.message", Language.EN),
                                getLocalizedDiscountName(discount), daysUntilExpiration),
                        NotificationType.PROMOTION,
                        discount.getId(),
                        "DISCOUNT",
                        "HIGH",
                        "alert-triangle",
                        "/discounts/" + discount.getId(),
                        createDiscountData(discount)
                );

                notificationService.createNotification(request);

            } catch (Exception e) {
                logger.warning("Failed to send dealer soon expiring notification to dealer " + dealer.getId() + ": " + e.getMessage());
            }
        }
    }

    // ==================== GENERAL NOTIFICATIONS (ALL USERS) ====================

    private void sendGeneralDiscountNotification(Discount discount, String action) {
        try {
            String title = createGeneralDiscountTitle(discount, action, Language.TR);
            String titleEn = createGeneralDiscountTitle(discount, action, Language.EN);
            String message = createGeneralDiscountMessage(discount, action, Language.TR);
            String messageEn = createGeneralDiscountMessage(discount, action, Language.EN);

            NotificationBroadcastRequest request =
                    new  NotificationBroadcastRequest(
                            title,
                            titleEn,
                            message,
                            messageEn,
                            NotificationType.PROMOTION,
                            discount.getId(),
                            "DISCOUNT",
                            "MEDIUM",
                            "percent",
                            "/discounts/" + discount.getId(),
                            createDiscountData(discount),
                            null, // specificUserIds
                            null, // targetRole
                            null, // targetDealerId
                            null,
                            true  // sendToAll
                    );

            notificationService.createNotificationForAllUsers(request);
            logger.info("General discount notification sent to all users");

        } catch (Exception e) {
            logger.severe("Failed to send general discount notification: " + e.getMessage());
        }
    }

    private void sendGeneralDiscountExpiredNotification(Discount discount) {
        try {
            NotificationBroadcastRequest request =
                    new NotificationBroadcastRequest(
                            getLocalizedMessage("discount.expired.title", Language.TR),
                            getLocalizedMessage("discount.expired.title", Language.EN),
                            String.format(getLocalizedMessage("discount.expired.general.message", Language.TR),
                                    getLocalizedDiscountName(discount)),
                            String.format(getLocalizedMessage("discount.expired.general.message", Language.EN),
                                    getLocalizedDiscountName(discount)),
                            NotificationType.PROMOTION,
                            discount.getId(),
                            "DISCOUNT",
                            "LOW",
                            "clock",
                            "/discounts",
                            createDiscountData(discount),
                            null, null, null,null,true
                    );

            notificationService.createNotificationForAllUsers(request);

        } catch (Exception e) {
            logger.severe("Failed to send general expired discount notification: " + e.getMessage());
        }
    }

    private void sendGeneralDiscountSoonExpiringNotification(Discount discount, int daysUntilExpiration) {
        try {
            com.maxx_global.dto.notification.NotificationBroadcastRequest request =
                    new com.maxx_global.dto.notification.NotificationBroadcastRequest(
                            getLocalizedMessage("discount.expiring.title", Language.TR),
                            getLocalizedMessage("discount.expiring.title", Language.EN),
                            String.format(getLocalizedMessage("discount.expiring.general.message", Language.TR),
                                    getLocalizedDiscountName(discount), daysUntilExpiration),
                            String.format(getLocalizedMessage("discount.expiring.general.message", Language.EN),
                                    getLocalizedDiscountName(discount), daysUntilExpiration),
                            NotificationType.PROMOTION,
                            discount.getId(),
                            "DISCOUNT",
                            "HIGH",
                            "alert-triangle",
                            "/discounts/" + discount.getId(),
                            createDiscountData(discount),
                            null, // specificUserIds
                            null, // targetRole
                            null, // targetDealerId
                            null,
                            true  // sendToAll
                    );

            notificationService.createNotificationForAllUsers(request);

        } catch (Exception e) {
            logger.severe("Failed to send general soon expiring discount notification: " + e.getMessage());
        }
    }

    // ==================== HELPER METHODS ====================

    private boolean hasSpecificDealers(Discount discount) {
        return discount.getApplicableDealers() != null && !discount.getApplicableDealers().isEmpty();
    }

    private boolean hasSpecificVariants(Discount discount) {
        return discount.getApplicableVariants() != null && !discount.getApplicableVariants().isEmpty();
    }

    private boolean isGeneralDiscount(Discount discount) {
        return !hasSpecificDealers(discount) && !hasSpecificVariants(discount);
    }

    private String createDealerDiscountTitle(Discount discount, String action, Language language) {
        String emoji = action.equals("CREATED") ? "🎉" : "📋";
        String actionText = action.equals("CREATED")
                ? getLocalizedMessage("discount.created.title", language)
                : getLocalizedMessage("discount.updated.title", language);

        return actionText + " " + emoji;
    }

    private String createDealerDiscountMessage(Discount discount, String action, Language language) {
        String actionText = action.equals("CREATED")
                ? getLocalizedMessage("discount.created.action", language)
                : getLocalizedMessage("discount.updated.action", language);
        String discountText = formatDiscountValue(discount, language);
        String productText = getVariantText(discount, language);
        String dateText = discount.getEndDate().format(DateTimeFormatter.ofPattern(getDatePattern(language)));

        // LocalizationService.getMessage parametrelerle birlikte çağırılmalı
        return localizationService.getMessage("discount.dealer.message", language.toLocale(),
                getLocalizedDiscountName(discount),
                actionText,
                discountText,
                productText,
                dateText);
    }

    private String createGeneralDiscountTitle(Discount discount, String action, Language language) {
        String emoji = action.equals("CREATED") ? "🎉" : "📋";
        String actionText = action.equals("CREATED")
                ? getLocalizedMessage("discount.created.title", language)
                : getLocalizedMessage("discount.updated.title", language);

        return actionText + " " + emoji;
    }

    private String createGeneralDiscountMessage(Discount discount, String action, Language language) {
        String actionText = action.equals("CREATED")
                ? getLocalizedMessage("discount.created.action", language)
                : getLocalizedMessage("discount.updated.action", language);
        String discountText = formatDiscountValue(discount, language);
        String productText = getVariantText(discount, language);
        String dateText = discount.getEndDate().format(DateTimeFormatter.ofPattern(getDatePattern(language)));

        // LocalizationService.getMessage parametrelerle birlikte çağırılmalı
        return localizationService.getMessage("discount.general.message", language.toLocale(),
                getLocalizedDiscountName(discount),
                actionText,
                discountText,
                productText,
                dateText);
    }

    private String formatDiscountValue(Discount discount, Language language) {
        if (discount.getDiscountType() == DiscountType.PERCENTAGE) {
            return "%" + discount.getDiscountValue().intValue() + (language == Language.EN ? " off" : " indirim");
        } else {
            return discount.getDiscountValue() + (language == Language.EN ? " TL off" : " TL indirim");
        }
    }

    private String getVariantText(Discount discount, Language language) {
        if (hasSpecificVariants(discount)) {
            int variantCount = discount.getApplicableVariants().size();
            if (variantCount == 1) {
                ProductVariant variant = discount.getApplicableVariants().iterator().next();
                return " - " + variant.getDisplayName() + (language == Language.EN ? " variant" : " varyantında");
            } else {
                return " - " + variantCount + (language == Language.EN ? " variants" : " varyantta");
            }
        }
        return language == Language.EN ? " - all variants" : " - tüm varyantlarda";
    }

    private String createDiscountData(Discount discount) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("discountId", discount.getId());
            Language language = localizationService.getCurrentLanguage();
            payload.put("discountName", getLocalizedDiscountName(discount));
            payload.put("discountDescription", discount.getLocalizedDescription(language));
            payload.put("discountType", discount.getDiscountType().name());
            payload.put("discountValue", discount.getDiscountValue());
            payload.put("endDate", discount.getEndDate());
            payload.put("hasUsageLimit", discount.getUsageLimit() != null);
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            logger.warning("Failed to serialize discount data: " + e.getMessage());
            return null;
        }
    }

    private String getLocalizedDiscountName(Discount discount) {
        Language language = localizationService.getCurrentLanguage();
        return discount.getLocalizedName(language);
    }

    private String getLocalizedMessage(String code, Language language) {
        return localizationService.getMessage(code, language.toLocale());
    }

    private String getDatePattern(Language language) {
        return language == Language.EN ? "MM.dd.yyyy" : "dd.MM.yyyy";
    }
}