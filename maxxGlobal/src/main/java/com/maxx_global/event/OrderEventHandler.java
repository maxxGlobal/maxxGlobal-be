// src/main/java/com/maxx_global/event/OrderEventHandler.java
package com.maxx_global.event;

import com.maxx_global.entity.AppUser;
import com.maxx_global.service.MailService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.util.logging.Logger;

@Component
public class OrderEventHandler {

    private static final Logger logger = Logger.getLogger(OrderEventHandler.class.getName());

    private final MailService mailService;
    private final NotificationEventService notificationEventService;

    public OrderEventHandler(MailService mailService, NotificationEventService notificationEventService) {
        this.mailService = mailService;
        this.notificationEventService = notificationEventService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            logger.info("Handling order created event for: " + event.order().getOrderNumber());
            notificationEventService.sendOrderCreatedNotification(event.order());
            mailService.sendNewOrderNotificationToAdmins(event.order());
        } catch (Exception e) {
            logger.severe("Error sending mail after order creation: " + e.getMessage());
            // Mail hatası sipariş oluşturulmasını engellememeli
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    public void handleOrderApproved(OrderApprovedEvent event) {
        try {
            notificationEventService.sendOrderApprovedNotification(event.order(),event.order().getUser());
            mailService.sendOrderApprovedNotificationToCustomer(event.order());
        } catch (Exception e) {
            logger.severe("Error sending approved order mail: " + e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    public void handleOrderRejected(OrderRejectedEvent event) {
        try {
            notificationEventService.sendOrderRejectedNotification(event.order(),event.order().getAdminNotes());
            mailService.sendOrderRejectedNotificationToCustomer(event.order());
        } catch (Exception e) {
            logger.severe("Error sending reject order mail: " + e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    public void handleOrderStatusChange(OrderStatusChangedEvent event) {
        try {
            notificationEventService.sendOrderStatusChangeNotification(event.order(),
                    event.order().getOrderStatus().getDisplayName(),event.order().getAdminNotes());
            mailService.sendOrderStatusChangeNotificationToCustomer(event.order(),event.previousStatus());
        } catch (Exception e) {
            logger.severe("Error sending reject order mail: " + e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    public void handleOrderEdited(OrderEditedEvent event) {
        try {
            notificationEventService.sendOrderEditedNotification(event.order());
            mailService.sendOrderEditedNotificationToCustomer(event.order());
        } catch (Exception e) {
            logger.severe("Error sending reject order mail: " + e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    public void handleOrderAutoCancelled(OrderAutoCancelledEvent event) {
        try {
            logger.info("Handling auto-cancelled order event for: " + event.order().getOrderNumber());
            notificationEventService.sendOrderAutoCancelledNotification(event.order(), event.reason(), event.hoursWaited());

            // Müşteriye mail gönder
            AppUser customer = event.order().getUser();
            if (customer != null && customer.getEmail() != null &&
                    !customer.getEmail().trim().isEmpty() &&
                    customer.isEmailNotificationsEnabled()) {
                mailService.sendOrderAutoCancelledNotificationToCustomer(event.order(), event.reason());
            }

            // Admin'lere de bildirim gönder
            mailService.sendOrderAutoCancelledNotificationToAdmins(event.order(), event.reason());

        } catch (Exception e) {
            logger.severe("Error sending auto-cancelled order mail: " + e.getMessage());
        }
    }

    // OrderEventHandler.java - Yeni event handler metodu ekle

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    public void handleOrderEditRejected(OrderEditRejectedEvent event) {
        try {
            logger.info("Handling order edit rejected event for: " + event.order().getOrderNumber());

            // Admin'lere bildirim gönder
            mailService.sendOrderEditRejectedNotificationToAdmins(event.order(), event.customerRejectionReason());

            // Notification service'e de bildir (varsa)
            if (notificationEventService != null) {
                notificationEventService.sendOrderEditRejectedNotification(
                        event.order(),
                        event.customerRejectionReason()
                );
            }
        } catch (Exception e) {
            logger.severe("Error sending order edit rejected mail: " + e.getMessage());
        }
    }

}