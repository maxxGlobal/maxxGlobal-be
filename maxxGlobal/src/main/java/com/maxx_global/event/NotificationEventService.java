package com.maxx_global.event;

import com.maxx_global.dto.appUser.AppUserResponse;
import com.maxx_global.dto.notification.NotificationRequest;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Notification;
import com.maxx_global.entity.Order;
import com.maxx_global.enums.NotificationStatus;
import com.maxx_global.enums.NotificationType;
import com.maxx_global.service.AppUserService;
import com.maxx_global.service.NotificationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

@Service
public class NotificationEventService {

    private static final Logger logger = Logger.getLogger(NotificationEventService.class.getName());

    private final NotificationService notificationService;
    private final AppUserService appUserService;

    public NotificationEventService(NotificationService notificationService, AppUserService appUserService) {
        this.notificationService = notificationService;
        this.appUserService = appUserService;
    }

    /**
     * Sipariş oluşturulduğunda bildirim gönder
     */
    public void sendOrderCreatedNotification(Order order) {
        logger.info("Sending order created notification for order: " + order.getOrderNumber());

        List<AppUser> users = appUserService.getUsersWithUserPermissions(List.of("ORDER_NOTIFICATION","SYSTEM_ADMIN"));

        try {
            NotificationRequest request = new NotificationRequest(
                    order.getUser().getId(),
                    "Yeni Sipariş Oluşturuldu 📝",
                    String.format("Sipariş numaranız: %s oluşturuldu. Toplam tutar: %.2f %s. " +
                                    "Siparişiniz onay için değerlendiriliyor.",
                            order.getOrderNumber(),
                            order.getTotalAmount(),
                            order.getCurrency()),
                    NotificationType.ORDER_CREATED,
                    order.getId(),
                    "ORDER",
                    "MEDIUM",
                    "shopping-cart",
                    "/orders/" + order.getId(),
                    null
            );

            NotificationRequest requestForAdmin = new NotificationRequest(
                    order.getUser().getId(),
                    "Yeni bir Sipariş var. 📝",
                    String.format("Sipariş numarası: %s oluşturuldu. Toplam tutar: %.2f %s. " +
                                    "Sipariş listesi sayfasından işlem yapabilirsiniz.",
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

            notificationService.createNotificationByEvent(request);
            notificationService.createNotificationForAdminByEvent(requestForAdmin, users);
            logger.info("Order created notification sent successfully");

        } catch (Exception e) {
            logger.severe("Error sending order created notification: " + e.getMessage());
        }
    }

    /**
     * Sipariş onaylandığında bildirim gönder
     */
    public void sendOrderApprovedNotification(Order order, AppUser approvedBy) {
        logger.info("Sending order approved notification for order: " + order.getOrderNumber());

        try {
            String approverName = approvedBy != null ?
                    approvedBy.getFirstName() + " " + approvedBy.getLastName() : "Yönetici";

            NotificationRequest request = new NotificationRequest(
                    order.getUser().getId(),
                    "Siparişiniz Onaylandı! ✅",
                    String.format("Sipariş numaranız %s onaylandı ve işleme alındı. " +
                                    "Sipariş durumunu takip edebilirsiniz.",
                            order.getOrderNumber()),
                    NotificationType.ORDER_APPROVED,
                    order.getId(),
                    "ORDER",
                    "HIGH",
                    "check-circle",
                    "/orders/" + order.getId(),
                    String.format("{\"approvedBy\":\"%s\",\"approvedAt\":\"%s\"}",
                            approverName, java.time.LocalDateTime.now())
            );

            notificationService.createNotification(request);
            logger.info("Order approved notification sent successfully");

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
                    order.getUser().getId(),
                    "Siparişiniz Reddedildi ❌",
                    String.format("Sipariş numaranız %s reddedildi. " +
                                    "Red nedeni: %s. Detaylar için sipariş sayfasını ziyaret edin.",
                            order.getOrderNumber(), reason),
                    NotificationType.ORDER_REJECTED,
                    order.getId(),
                    "ORDER",
                    "HIGH",
                    "x-circle",
                    "/orders/" + order.getId(),
                    String.format("{\"rejectionReason\":\"%s\"}", reason)
            );

            notificationService.createNotification(request);
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
                    order.getUser().getId(),
                    "Siparişiniz Düzenlendi 📝",
                    String.format("Sipariş numaranız %s yönetici tarafından düzenlendi. " +
                                    "Değişiklikleri inceleyin ve onaylayın.",
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

            notificationService.createNotification(request);
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
            String message = getStatusChangeMessage(order.getOrderNumber(), newStatus, statusNote);
            String icon = getStatusChangeIcon(newStatus);
            NotificationType notificationType = getStatusChangeNotificationType(newStatus);
            String priority = getStatusChangePriority(newStatus);

            NotificationRequest request = new NotificationRequest(
                    order.getUser().getId(),
                    title,
                    message,
                    notificationType,
                    order.getId(),
                    "ORDER",
                    priority,
                    icon,
                    "/orders/" + order.getId(),
                    String.format("{\"newStatus\":\"%s\",\"statusNote\":\"%s\",\"changedAt\":\"%s\"}",
                            newStatus, statusNote != null ? statusNote : "", java.time.LocalDateTime.now())
            );

            notificationService.createNotification(request);
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
            for (AppUser admin : adminUsers) {
                try {
                    Notification notification = new Notification();
                    notification.setTitle(title);
                    notification.setMessage(message);
                    notification.setType(NotificationType.ORDER_REJECTED); // Mevcut type kullan
                    notification.setNotificationStatus(NotificationStatus.UNREAD);
                    notification.setRelatedEntityType("ORDER");
                    notification.setRelatedEntityId(order.getId());
                    notification.setPriority("HIGH"); // Yüksek öncelik

                    // Ek data - Manual JSON string oluştur
                    String dataJson = String.format(
                            "{\"orderId\":%d,\"orderNumber\":\"%s\",\"customerName\":\"%s\",\"dealerName\":\"%s\",\"totalAmount\":\"%s\",\"rejectionReason\":\"%s\",\"rejectionTime\":\"%s\",\"action\":\"ORDER_EDIT_REJECTED\"}",
                            order.getId(),
                            order.getOrderNumber(),
                            order.getUser().getFirstName() + " " + order.getUser().getLastName(),
                            order.getUser().getDealer().getName(),
                            order.getTotalAmount().toString(),
                            rejectionReason != null ? rejectionReason.replace("\"", "\\\"") : "", // Escape quotes
                            LocalDateTime.now()
                    );
                    notification.setData(dataJson);

                    notificationService.saveNotification(notification);
                    logger.info("Order edit rejected notification created for admin: " + admin.getEmail());

                } catch (Exception e) {
                    logger.severe("Error creating order edit rejected notification for admin " +
                            admin.getEmail() + ": " + e.getMessage());
                }
            }

            logger.info("Order edit rejected notifications created for " + adminUsers.size() + " admins");

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
                    order.getUser().getId(),
                    "Siparişiniz Otomatik İptal Edildi ⏰",
                    String.format("Sipariş numaranız %s, %d saat onay bekledikten sonra " +
                                    "sistem tarafından otomatik olarak iptal edildi. " +
                                    "Sebep: %s",
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

            notificationService.createNotification(request);
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