package com.maxx_global.event;

import com.maxx_global.dto.appUser.AppUserResponse;
import com.maxx_global.dto.notification.NotificationRequest;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Order;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.enums.NotificationType;
import com.maxx_global.repository.AppUserRepository;
import com.maxx_global.service.AppUserService;
import com.maxx_global.service.NotificationService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

@Service
public class NotificationEventService {

    private static final Logger logger = Logger.getLogger(NotificationEventService.class.getName());

    private final NotificationService notificationService;
    private final AppUserService appUserService;
    private final AppUserRepository appUserRepository;

    public NotificationEventService(NotificationService notificationService,
                                   AppUserService appUserService,
                                   AppUserRepository appUserRepository) {
        this.notificationService = notificationService;
        this.appUserService = appUserService;
        this.appUserRepository = appUserRepository;
    }

    /**
     * Fiyat bilgisini yetkiye göre formatla
     */
    private String formatPrice(BigDecimal amount, String currency, boolean canViewPrice) {
        if (!canViewPrice) {
            return "***";
        }
        return String.format("%.2f %s", amount, currency);
    }

    /**
     * Kullanıcı için özelleştirilmiş bildirim mesajı oluştur
     */
    private String createOrderCreatedMessage(Order order, AppUser recipient) {
        boolean canViewPrice = recipient.canViewPrice();
        String priceInfo = canViewPrice ?
            String.format("Toplam tutar: %s. ", formatPrice(order.getTotalAmount(), order.getCurrency().name(), true)) :
            "";

        return String.format("Sipariş numaranız: %s oluşturuldu. %sSiparişiniz onay için değerlendiriliyor.",
                order.getOrderNumber(), priceInfo);
    }

    private List<AppUser> resolveOrderRecipients(Order order) {
        if (order == null) {
            return Collections.emptyList();
        }

        List<AppUser> recipients = new ArrayList<>();
        Set<Long> processedIds = new HashSet<>();

        addRecipientIfEligible(order.getUser(), recipients, processedIds);

        AppUser customer = order.getUser();
        if (customer != null && customer.getDealer() != null) {
            List<AppUser> authorizedUsers = appUserRepository.findAuthorizedUsersForDealer(customer.getDealer().getId());
            for (AppUser authorizedUser : authorizedUsers) {
                addRecipientIfEligible(authorizedUser, recipients, processedIds);
            }
        }

        return recipients;
    }

    private void addRecipientIfEligible(AppUser user, List<AppUser> recipients, Set<Long> processedIds) {
        if (user == null || user.getId() == null || recipients == null || processedIds == null) {
            return;
        }

        if (user.getStatus() != null && user.getStatus() != EntityStatus.ACTIVE) {
            return;
        }

        if (!user.isEmailNotificationsEnabled()) {
            return;
        }

        if (processedIds.add(user.getId())) {
            recipients.add(user);
        }
    }

    private Long resolveDealerId(Order order) {
        if (order == null || order.getUser() == null || order.getUser().getDealer() == null) {
            return null;
        }
        return order.getUser().getDealer().getId();
    }

    /**
     * Sipariş oluşturulduğunda bildirim gönder - Bayinin tüm kullanıcılarına
     */
    public void sendOrderCreatedNotification(Order order) {
        logger.info("Sending order created notification for order: " + order.getOrderNumber());

        List<AppUser> adminUsers = appUserService.getUsersWithUserPermissions(List.of("ORDER_NOTIFICATION","SYSTEM_ADMIN"));

        try {
            AppUser customer = order.getUser();

            if (customer.getDealer() == null) {
                logger.warning("Customer has no dealer, cannot send notification");
                return;
            }

            List<AppUser> dealerUsers = resolveOrderRecipients(order);

            if (dealerUsers.isEmpty()) {
                logger.warning("No eligible dealer recipients found for order: " + order.getOrderNumber());
            } else {
                String localizedMessage = createOrderCreatedMessage(order, dealerUsers.get(0));
                NotificationRequest dealerRequest = new NotificationRequest(
                        resolveDealerId(order),
                        "Yeni Sipariş Oluşturuldu 📝",
                        "New Order Created 📝",
                        localizedMessage,
                        localizedMessage,
                        NotificationType.ORDER_CREATED,
                        order.getId(),
                        "ORDER",
                        "MEDIUM",
                        "shopping-cart",
                        "/orders/" + order.getId(),
                        null
                );

                notificationService.createNotification(dealerRequest, dealerUsers);
            }

            // Admin'lere bildirim gönder
            NotificationRequest requestForAdmin = new NotificationRequest(
                    resolveDealerId(order),
                    "Yeni bir Sipariş var. 📝",
                    "New order created 📝",
                    String.format("Sipariş numarası: %s oluşturuldu. Toplam tutar: %.2f %s. " +
                                    "Sipariş listesi sayfasından işlem yapabilirsiniz.",
                            order.getOrderNumber(),
                            order.getTotalAmount(),
                            order.getCurrency()),
                    String.format("Order number %s created. Total amount: %.2f %s. " +
                                    "You can review it from the orders page.",
                            order.getOrderNumber(),
                            order.getTotalAmount(),
                            order.getCurrency()),
                    NotificationType.ORDER_CREATED,
                    order.getId(),
                    "ORDER",
                    "MEDIUM",
                    "shopping-cart",
                    "admin/orders/" + order.getId(),
                    null
            );

            notificationService.createNotificationForAdminByEvent(requestForAdmin, adminUsers);
            logger.info("Order created notification sent successfully");

        } catch (Exception e) {
            logger.severe("Error sending order created notification: " + e.getMessage());
        }
    }

    /**
     * Sipariş onaylandığında bildirim gönder - Bayinin tüm kullanıcılarına
     */
    public void sendOrderApprovedNotification(Order order, AppUser approvedBy) {
        logger.info("Sending order approved notification for order: " + order.getOrderNumber());

        try {
            AppUser customer = order.getUser();
            String approverName = approvedBy != null ?
                    approvedBy.getFirstName() + " " + approvedBy.getLastName() : "Yönetici";

            if (customer.getDealer() == null) {
                logger.warning("Customer has no dealer, cannot send notification");
                return;
            }

            List<AppUser> dealerUsers = resolveOrderRecipients(order);

            if (dealerUsers.isEmpty()) {
                logger.warning("No eligible dealer recipients found for approved order: " + order.getOrderNumber());
            } else {
                String message = String.format("Sipariş numaranız %s onaylandı ve işleme alındı. " +
                                "Sipariş durumunu takip edebilirsiniz.",
                        order.getOrderNumber());

                NotificationRequest request = new NotificationRequest(
                        resolveDealerId(order),
                        "Siparişiniz Onaylandı! ✅",
                        "Your order is approved ✅",
                        message,
                        message,
                        NotificationType.ORDER_APPROVED,
                        order.getId(),
                        "ORDER",
                        "HIGH",
                        "check-circle",
                        "/orders/" + order.getId(),
                        String.format("{\"approvedBy\":\"%s\",\"approvedAt\":\"%s\"}",
                                approverName, LocalDateTime.now())
                );

                notificationService.createNotification(request, dealerUsers);
            }

        } catch (Exception e) {
            logger.severe("Error sending order approved notification: " + e.getMessage());
        }
    }

    /**
     * Sipariş reddedildiğinde bildirim gönder
     */
    public void sendOrderRejectedNotification(Order order, String rejectionReason) {
        logger.info("Sending order rejected notification for order: " + order.getOrderNumber());

        try {
            String reason = rejectionReason != null && !rejectionReason.trim().isEmpty() ?
                    rejectionReason : "Red nedeni belirtilmemiş";

            NotificationRequest request = new NotificationRequest(
                    resolveDealerId(order),
                    "Siparişiniz Reddedildi ❌",
                    "Your order was rejected ❌",
                    String.format("Sipariş numaranız %s reddedildi. " +
                                    "Red nedeni: %s. Detaylar için sipariş sayfasını ziyaret edin.",
                            order.getOrderNumber(), reason),
                    String.format("Your order %s was rejected. " +
                                    "Reason: %s. Visit the order page for details.",
                            order.getOrderNumber(), reason),
                    NotificationType.ORDER_REJECTED,
                    order.getId(),
                    "ORDER",
                    "HIGH",
                    "x-circle",
                    "/orders/" + order.getId(),
                    String.format("{\"rejectionReason\":\"%s\"}", reason)
            );

            List<AppUser> recipients = resolveOrderRecipients(order);
            if (recipients.isEmpty()) {
                logger.warning("No eligible recipients for rejected order notification: " + order.getOrderNumber());
                return;
            }

            notificationService.createNotification(request, recipients);
            logger.info("Order rejected notification sent successfully");

        } catch (Exception e) {
            logger.severe("Error sending order rejected notification: " + e.getMessage());
        }
    }

    /**
     * Sipariş düzenlendiğinde bildirim gönder
     */
    public void sendOrderEditedNotification(Order order) {
        logger.info("Sending order edited notification for order: " + order.getOrderNumber());

        try {
            AppUserResponse editedBy = appUserService.getUserById(order.getUpdatedBy());
            String editorName = editedBy != null ?
                    editedBy.firstName() + " " + editedBy.lastName() : "Yönetici";

            NotificationRequest request = new NotificationRequest(
                    resolveDealerId(order),
                    "Siparişiniz Düzenlendi 📝",
                    "Your order was edited 📝",
                    String.format("Sipariş numaranız %s yönetici tarafından düzenlendi. " +
                                    "Değişiklikleri inceleyin ve onaylayın.",
                            order.getOrderNumber()),
                    String.format("Your order %s was edited by an administrator. " +
                                    "Please review and approve the changes.",
                            order.getOrderNumber()),
                    NotificationType.ORDER_EDITED,
                    order.getId(),
                    "ORDER",
                    "HIGH",
                    "edit",
                    "/orders/" + order.getId() + "/edited",
                    String.format("{\"editedBy\":\"%s\",\"editedAt\":\"%s\"}",
                            editorName, java.time.LocalDateTime.now())
            );

            List<AppUser> recipients = resolveOrderRecipients(order);
            if (recipients.isEmpty()) {
                logger.warning("No eligible recipients for edited order notification: " + order.getOrderNumber());
                return;
            }

            notificationService.createNotification(request, recipients);
            logger.info("Order edited notification sent successfully");

        } catch (Exception e) {
            logger.severe("Error sending order edited notification: " + e.getMessage());
        }
    }

    /**
     * Sipariş durumu değiştiğinde bildirim gönder
     */
    public void sendOrderStatusChangeNotification(Order order, String newStatus, String statusNote) {
        logger.info("Sending order status change notification for order: " + order.getOrderNumber() +
                " to status: " + newStatus);

        try {
            String title = getStatusChangeTitle(newStatus);
            String titleEn = getStatusChangeTitleEn(newStatus);
            String message = getStatusChangeMessage(order.getOrderNumber(), newStatus, statusNote);
            String messageEn = getStatusChangeMessageEn(order.getOrderNumber(), newStatus, statusNote);
            String icon = getStatusChangeIcon(newStatus);
            NotificationType notificationType = getStatusChangeNotificationType(newStatus);
            String priority = getStatusChangePriority(newStatus);

            NotificationRequest request = new NotificationRequest(
                    resolveDealerId(order),
                    title,
                    titleEn,
                    message,
                    messageEn,
                    notificationType,
                    order.getId(),
                    "ORDER",
                    priority,
                    icon,
                    "/orders/" + order.getId(),
                    String.format("{\"newStatus\":\"%s\",\"statusNote\":\"%s\",\"changedAt\":\"%s\"}",
                            newStatus, statusNote != null ? statusNote : "", java.time.LocalDateTime.now())
            );

            List<AppUser> recipients = resolveOrderRecipients(order);
            if (recipients.isEmpty()) {
                logger.warning("No eligible recipients for status change notification: " + order.getOrderNumber());
                return;
            }

            notificationService.createNotification(request, recipients);
            logger.info("Order status change notification sent successfully");

        } catch (Exception e) {
            logger.severe("Error sending order status change notification: " + e.getMessage());
        }
    }

    // NotificationEventService.java - Yeni metod ekle

    /**
     * Müşteri düzenlenmiş siparişi reddettiğinde admin'lere notification gönder
     */
    // NotificationEventService.java - Yeni metod ekle

    /**
     * Müşteri düzenlenmiş siparişi reddettiğinde admin'lere notification gönder
     */
    public void sendOrderEditRejectedNotification(Order order, String rejectionReason) {
        try {
            logger.info("Creating order edit rejected notifications for order: " + order.getOrderNumber());

            // Admin ve super admin kullanıcılarını getir
            List<AppUser> adminUsers = appUserService.getUsersWithUserPermissions(Collections.singletonList("ORDER_NOTIFICATION"));

            if (adminUsers.isEmpty()) {
                logger.warning("No admin users found for order edit rejected notification");
                return;
            }

            String title = "Müşteri Düzenlemeyi Reddetti";
            String message = String.format(
                    "Müşteri %s, sipariş %s için yapılan düzenlemeleri reddetti. " +
                            "Red nedeni: %s. Sipariş iptal edildi ve stoklar iade edildi.",
                    order.getUser().getFirstName() + " " + order.getUser().getLastName(),
                    order.getOrderNumber(),
                    rejectionReason != null && !rejectionReason.trim().isEmpty()
                            ? rejectionReason
                            : "Belirtilmemiş"
            );

            // Her admin için notification oluştur
            String dataJson = String.format(
                    "{\"orderId\":%d,\"orderNumber\":\"%s\",\"customerName\":\"%s\",\"dealerName\":\"%s\",\"totalAmount\":\"%s\",\"rejectionReason\":\"%s\",\"rejectionTime\":\"%s\",\"action\":\"ORDER_EDIT_REJECTED\"}" ,
                    order.getId(),
                    order.getOrderNumber(),
                    order.getUser().getFirstName() + " " + order.getUser().getLastName(),
                    order.getUser().getDealer().getName(),
                    order.getTotalAmount().toString(),
                    rejectionReason != null ? rejectionReason.replace("\"", "\\\"") : "",
                    LocalDateTime.now()
            );

            NotificationRequest adminRequest = new NotificationRequest(
                    resolveDealerId(order),
                    title,
                    title,
                    message,
                    message,
                    NotificationType.ORDER_REJECTED,
                    order.getId(),
                    "ORDER",
                    "HIGH",
                    "bell",
                    null,
                    dataJson
            );

            notificationService.createNotification(adminRequest, adminUsers);

        } catch (Exception e) {
            logger.severe("Error sending order edit rejected notifications: " + e.getMessage());
            // Bu hata notification gönderimini engellemez, sadece log'lanır
        }
    }

    /**
     * Otomatik iptal bildirimi gönder
     */
    public void sendOrderAutoCancelledNotification(Order order, String reason, int hoursWaited) {
        logger.info("Sending order auto-cancelled notification for order: " + order.getOrderNumber());

        try {
            NotificationRequest request = new NotificationRequest(
                    resolveDealerId(order),
                    "Siparişiniz Otomatik İptal Edildi ⏰",
                    "Your order was automatically cancelled ⏰",
                    String.format("Sipariş numaranız %s, %d saat onay bekledikten sonra " +
                                    "sistem tarafından otomatik olarak iptal edildi. " +
                                    "Sebep: %s",
                            order.getOrderNumber(), hoursWaited, reason),
                    String.format("Order %s was automatically cancelled after waiting %d hours for approval. Reason: %s",
                            order.getOrderNumber(), hoursWaited, reason),
                    NotificationType.ORDER_CANCELLED,
                    order.getId(),
                    "ORDER",
                    "HIGH",
                    "clock",
                    "/orders/" + order.getId(),
                    String.format("{\"autoCancelReason\":\"%s\",\"hoursWaited\":%d}",
                            reason, hoursWaited)
            );

            List<AppUser> recipients = resolveOrderRecipients(order);
            if (recipients.isEmpty()) {
                logger.warning("No eligible recipients for auto-cancel notification: " + order.getOrderNumber());
                return;
            }

            notificationService.createNotification(request, recipients);
            logger.info("Order auto-cancelled notification sent successfully");

        } catch (Exception e) {
            logger.severe("Error sending order auto-cancelled notification: " + e.getMessage());
        }
    }

    // Helper metodlar
    private String getStatusChangeTitle(String status) {
        return switch (status.toUpperCase()) {
            case "SHIPPED" -> "Siparişiniz Kargoya Verildi 🚚";
            case "DELIVERED" -> "Siparişiniz Teslim Edildi ✅";
            case "COMPLETED" -> "Siparişiniz Tamamlandı 🎉";
            case "CANCELLED" -> "Siparişiniz İptal Edildi ❌";
            case "PENDING" -> "Siparişiniz Onay Bekliyor ⏳";
            case "APPROVED" -> "Siparişiniz Onaylandı ✅";
            default -> "Sipariş Durumu Güncellendi 📋";
        };
    }

    private String getStatusChangeTitleEn(String status) {
        return switch (status.toUpperCase()) {
            case "SHIPPED" -> "Your order has been shipped 🚚";
            case "DELIVERED" -> "Your order has been delivered ✅";
            case "COMPLETED" -> "Your order is completed 🎉";
            case "CANCELLED" -> "Your order was cancelled ❌";
            case "PENDING" -> "Your order is pending approval ⏳";
            case "APPROVED" -> "Your order was approved ✅";
            default -> "Order status updated 📋";
        };
    }

    private String getStatusChangeMessage(String orderNumber, String status, String statusNote) {
        String baseMessage = switch (status.toUpperCase()) {
            case "SHIPPED" -> "Sipariş numaranız %s kargoya verildi. Kargo takip bilgileri e-posta ile gönderilecek.";
            case "DELIVERED" -> "Sipariş numaranız %s başarıyla teslim edildi. Teşekkür ederiz!";
            case "COMPLETED" -> "Sipariş numaranız %s tamamlandı. Bizi tercih ettiğiniz için teşekkürler.";
            case "CANCELLED" -> "Sipariş numaranız %s iptal edildi.";
            case "PENDING" -> "Sipariş numaranız %s onay için bekliyor.";
            case "APPROVED" -> "Sipariş numaranız %s onaylandı ve işleme alındı.";
            default -> "Sipariş numaranız %s durumu güncellendi: " + status;
        };

        String message = String.format(baseMessage, orderNumber);

        if (statusNote != null && !statusNote.trim().isEmpty()) {
            message += " Not: " + statusNote;
        }

        return message;
    }

    private String getStatusChangeMessageEn(String orderNumber, String status, String statusNote) {
        String baseMessage = switch (status.toUpperCase()) {
            case "SHIPPED" -> "Your order %s has been shipped. Tracking info will be emailed.";
            case "DELIVERED" -> "Your order %s has been delivered successfully. Thank you!";
            case "COMPLETED" -> "Your order %s is completed. Thank you for choosing us.";
            case "CANCELLED" -> "Your order %s was cancelled.";
            case "PENDING" -> "Your order %s is waiting for approval.";
            case "APPROVED" -> "Your order %s has been approved and is being processed.";
            default -> "Your order %s status has been updated: " + status;
        };

        String message = String.format(baseMessage, orderNumber);

        if (statusNote != null && !statusNote.trim().isEmpty()) {
            message += " Note: " + statusNote;
        }

        return message;
    }

    private String getStatusChangeIcon(String status) {
        return switch (status.toUpperCase()) {
            case "SHIPPED" -> "truck";
            case "DELIVERED", "COMPLETED" -> "check-circle";
            case "CANCELLED" -> "x-circle";
            case "PENDING" -> "clock";
            case "APPROVED" -> "check";
            default -> "info";
        };
    }

    private NotificationType getStatusChangeNotificationType(String status) {
        return switch (status.toUpperCase()) {
            case "SHIPPED" -> NotificationType.ORDER_SHIPPED;
            case "DELIVERED", "COMPLETED" -> NotificationType.ORDER_DELIVERED;
            case "CANCELLED" -> NotificationType.ORDER_CANCELLED;
            case "APPROVED" -> NotificationType.ORDER_APPROVED;
            default -> NotificationType.ORDER_CREATED;
        };
    }

    private String getStatusChangePriority(String status) {
        return switch (status.toUpperCase()) {
            case "DELIVERED", "COMPLETED", "CANCELLED" -> "HIGH";
            case "SHIPPED", "APPROVED" -> "MEDIUM";
            default -> "LOW";
        };
    }
}
