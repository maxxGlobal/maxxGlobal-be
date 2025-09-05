package com.maxx_global.event;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

import java.util.logging.Logger;

@Component
public class DiscountEventHandler {

    private static final Logger logger = Logger.getLogger(DiscountEventHandler.class.getName());

    private final DiscountNotificationEventService discountNotificationEventService;

    public DiscountEventHandler(DiscountNotificationEventService discountNotificationEventService) {
        this.discountNotificationEventService = discountNotificationEventService;
    }

    /**
     * Yeni indirim oluşturulduğunda çalışır
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    public void handleDiscountCreated(DiscountCreatedEvent event) {
        try {
            logger.info("Handling discount created event for: " + event.discount().getName());
            discountNotificationEventService.sendDiscountCreatedNotification(event.discount());

        } catch (Exception e) {
            logger.severe("Error handling discount created event: " + e.getMessage());
            // Hata bildirim gönderimini engellemez, sadece log'lanır
        }
    }

    /**
     * İndirim güncellendiğinde çalışır
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    public void handleDiscountUpdated(DiscountUpdatedEvent event) {
        try {
            logger.info("Handling discount updated event for: " + event.discount().getName() +
                    " (activation changed: " + event.isActivationChanged() +
                    ", date changed: " + event.isDateChanged() + ")");

            discountNotificationEventService.sendDiscountUpdatedNotification(
                    event.discount(),
                    event.isActivationChanged(),
                    event.isDateChanged()
            );

        } catch (Exception e) {
            logger.severe("Error handling discount updated event: " + e.getMessage());
        }
    }

    /**
     * İndirim süresi dolduğunda çalışır
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    public void handleDiscountExpired(DiscountExpiredEvent event) {
        try {
            logger.info("Handling discount expired event for: " + event.discount().getName());
            discountNotificationEventService.sendDiscountExpiredNotification(event.discount());

        } catch (Exception e) {
            logger.severe("Error handling discount expired event: " + e.getMessage());
        }
    }

    /**
     * İndirim süresi yakında dolacağında çalışır
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    public void handleDiscountSoonExpiring(DiscountSoonExpiringEvent event) {
        try {
            logger.info("Handling discount soon expiring event for: " + event.discount().getName() +
                    " (expires in " + event.daysUntilExpiration() + " days)");

            discountNotificationEventService.sendDiscountSoonExpiringNotification(
                    event.discount(),
                    event.daysUntilExpiration()
            );

        } catch (Exception e) {
            logger.severe("Error handling discount soon expiring event: " + e.getMessage());
        }
    }
}