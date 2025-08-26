// src/main/java/com/maxx_global/event/OrderEventHandler.java
package com.maxx_global.event;

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

    public OrderEventHandler(MailService mailService) {
        this.mailService = mailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    public void handleOrderCreated(OrderCreatedEvent event) {
        try {
            logger.info("Handling order created event for: " + event.order().getOrderNumber());
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
            mailService.sendOrderApprovedNotificationToCustomer(event.order());
        } catch (Exception e) {
            logger.severe("Error sending approved order mail: " + e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    public void handleOrderRejected(OrderRejectedEvent event) {
        try {
            mailService.sendOrderRejectedNotificationToCustomer(event.order());
        } catch (Exception e) {
            logger.severe("Error sending reject order mail: " + e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    public void handleOrderStatusChange(OrderStatusChangedEvent event) {
        try {
            mailService.sendOrderStatusChangeNotificationToCustomer(event.order(),event.previousStatus());
        } catch (Exception e) {
            logger.severe("Error sending reject order mail: " + e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    public void handleOrderEdited(OrderEditedEvent event) {
        try {
            mailService.sendOrderEditedNotificationToCustomer(event.order());
        } catch (Exception e) {
            logger.severe("Error sending reject order mail: " + e.getMessage());
        }
    }

    // Diğer order event'leri için de benzer handler'lar...
}