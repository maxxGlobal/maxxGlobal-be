package com.maxx_global.event;

import com.maxx_global.dto.notification.NotificationBroadcastRequest;
import com.maxx_global.dto.notification.NotificationRequest;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.Discount;
import com.maxx_global.entity.ProductVariant;
import com.maxx_global.enums.DiscountType;
import com.maxx_global.enums.NotificationType;
import com.maxx_global.service.NotificationService;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

@Service
public class DiscountNotificationEventService {

    private static final Logger logger = Logger.getLogger(DiscountNotificationEventService.class.getName());

    private final NotificationService notificationService;

    public DiscountNotificationEventService(NotificationService notificationService) {
        this.notificationService = notificationService;
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
            // 2. Product bazlı veya genel indirim ise tüm kullanıcılara gönder
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
                String title = createDealerDiscountTitle(discount, action);
                String message = createDealerDiscountMessage(discount, action);

                NotificationRequest request = new NotificationRequest(
                        dealer.getId(),
                        title,
                        message,
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
                        "İndirim Kampanyası Sona Erdi ⏰",
                        String.format("'%s' indirim kampanyası sona erdi. " +
                                        "Yeni kampanyalarımızı kaçırmayın!",
                                discount.getName()),
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
                        "İndirim Kampanyası Yakında Bitiyor! ⚠️",
                        String.format("'%s' indirim kampanyası %d gün sonra sona erecek. " +
                                        "Son fırsatı kaçırmayın!",
                                discount.getName(), daysUntilExpiration),
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
            String title = createGeneralDiscountTitle(discount, action);
            String message = createGeneralDiscountMessage(discount, action);

            NotificationBroadcastRequest request =
                    new  NotificationBroadcastRequest(
                            title,
                            message,
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
                            "İndirim Kampanyası Sona Erdi ⏰",
                            String.format("'%s' indirim kampanyası sona erdi. " +
                                            "Yeni kampanyalarımızı takip etmeyi unutmayın!",
                                    discount.getName()),
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
                            "İndirim Kampanyası Yakında Bitiyor! ⚠️",
                            String.format("'%s' indirim kampanyası %d gün sonra sona erecek. " +
                                            "Son fırsatlardan yararlanın!",
                                    discount.getName(), daysUntilExpiration),
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

    private String createDealerDiscountTitle(Discount discount, String action) {
        String emoji = action.equals("CREATED") ? "🎉" : "📋";
        String actionText = action.equals("CREATED") ? "Yeni İndirim Kampanyası!" : "İndirim Kampanyası Güncellendi!";

        return actionText + " " + emoji;
    }

    private String createDealerDiscountMessage(Discount discount, String action) {
        String actionText = action.equals("CREATED") ? "başladı" : "güncellendi";
        String discountText = formatDiscountValue(discount);
        String productText = getVariantText(discount);
        String dateText = discount.getEndDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        return String.format("Bayi bünyenizde '%s' indirim kampanyası %s! " +
                        "%s%s Son gün: %s. Detaylar için tıklayın.",
                discount.getName(),
                actionText,
                discountText,
                productText,
                dateText);
    }

    private String createGeneralDiscountTitle(Discount discount, String action) {
        String emoji = action.equals("CREATED") ? "🎉" : "📋";
        String actionText = action.equals("CREATED") ? "Yeni İndirim Kampanyası!" : "İndirim Kampanyası Güncellendi!";

        return actionText + " " + emoji;
    }

    private String createGeneralDiscountMessage(Discount discount, String action) {
        String actionText = action.equals("CREATED") ? "başladı" : "güncellendi";
        String discountText = formatDiscountValue(discount);
        String productText = getVariantText(discount);
        String dateText = discount.getEndDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        return String.format("'%s' indirim kampanyası %s! " +
                        "%s%s Son gün: %s. Fırsatı kaçırmayın!",
                discount.getName(),
                actionText,
                discountText,
                productText,
                dateText);
    }

    private String formatDiscountValue(Discount discount) {
        if (discount.getDiscountType() == DiscountType.PERCENTAGE) {
            return "%" + discount.getDiscountValue().intValue() + " indirim";
        } else {
            return discount.getDiscountValue() + " TL indirim";
        }
    }

    private String getVariantText(Discount discount) {
        if (hasSpecificVariants(discount)) {
            int variantCount = discount.getApplicableVariants().size();
            if (variantCount == 1) {
                ProductVariant variant = discount.getApplicableVariants().iterator().next();
                StringBuilder builder = new StringBuilder();
                if (variant.getProduct() != null && variant.getProduct().getName() != null) {
                    builder.append(variant.getProduct().getName());
                }
                if (variant.getSize() != null && !variant.getSize().isBlank()) {
                    if (builder.length() > 0) {
                        builder.append(" - ");
                    }
                    builder.append(variant.getSize());
                }
                return " - " + builder + " varyantında";
            } else {
                return " - " + variantCount + " varyantta";
            }
        }
        return " - tüm varyantlarda";
    }

    private String createDiscountData(Discount discount) {
        return String.format("{\"discountId\":%d,\"discountName\":\"%s\",\"discountType\":\"%s\"," +
                        "\"discountValue\":%s,\"endDate\":\"%s\",\"hasUsageLimit\":%s}",
                discount.getId(),
                discount.getName(),
                discount.getDiscountType().name(),
                discount.getDiscountValue(),
                discount.getEndDate(),
                discount.getUsageLimit() != null);
    }
}