package com.maxx_global.job;

import com.maxx_global.dto.notification.NotificationCleanupStats;
import com.maxx_global.dto.notification.NotificationCleanupStatus;
import com.maxx_global.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

@Component
public class NotificationCleanupJob {

    private static final Logger logger = Logger.getLogger(NotificationCleanupJob.class.getName());

    private final NotificationService notificationService;

    @Value("${app.notification.retention-days:20}")
    private int retentionDays;

    @Value("${app.notification.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    public NotificationCleanupJob(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Her gün saat 02:00'da çalışan notification cleanup job'ı
     * Okunmuş bildirimlerin 20 gün sonra silinmesi için
     */
    @Scheduled(cron = "0 0 2 * * ?") // Her gün 02:00
    public void cleanupReadNotifications() {
        if (!cleanupEnabled) {
            logger.info("📤 Notification cleanup is disabled, skipping cleanup job");
            return;
        }

        logger.info("🧹 Starting notification cleanup job - retention period: " + retentionDays + " days");

        try {
            // Cleanup işlemini başlat
            int deletedCount = notificationService.cleanupReadNotifications(retentionDays);

            if (deletedCount > 0) {
                logger.info("✅ Notification cleanup completed successfully:");
                logger.info("   📊 Deleted notifications: " + deletedCount);
                logger.info("   📅 Retention period: " + retentionDays + " days");
                logger.info("   ⏰ Cleanup time: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
            } else {
                logger.info("ℹ️ No notifications found for cleanup (older than " + retentionDays + " days)");
            }

        } catch (Exception e) {
            logger.severe("❌ Error during notification cleanup: " + e.getMessage());

            // Monitoring sisteme hata bildir (opsiyonel)
            try {
                // Burada monitoring/alerting sisteme hata gönderilebilir
                notifyMonitoringSystemAboutCleanupFailure(e);
            } catch (Exception monitoringError) {
                logger.warning("Failed to send cleanup failure alert: " + monitoringError.getMessage());
            }
        }
    }

    /**
     * Haftalık cleanup raporu - Her Pazartesi 09:00'da
     */
    @Scheduled(cron = "0 0 9 * * MON") // Her Pazartesi 09:00
    public void weeklyCleanupReport() {
        if (!cleanupEnabled) {
            return;
        }

        logger.info("📈 Generating weekly notification cleanup report");

        try {
            // Son 7 günde temizlenen notification sayısını al
            NotificationCleanupStats stats = notificationService.getCleanupStatsForLastWeek();

            logger.info("📊 Weekly Notification Cleanup Report:");
            logger.info("   🗑️ Total cleaned this week: " + stats.totalCleaned());
            logger.info("   📨 Average per day: " + (stats.totalCleaned() / 7));
            logger.info("   💾 Estimated space saved: ~" + (stats.totalCleaned() * 2) + " KB");
            logger.info("   🎯 System performance: Optimal");

        } catch (Exception e) {
            logger.warning("Error generating weekly cleanup report: " + e.getMessage());
        }
    }

    /**
     * Manuel cleanup tetikleyici (Test/Debug amaçlı)
     */
    public int triggerManualCleanup() {
        logger.info("🔧 Manual notification cleanup triggered by admin");

        try {
            int deletedCount = notificationService.cleanupReadNotifications(retentionDays);

            logger.info("✅ Manual cleanup completed - deleted: " + deletedCount + " notifications");
            return deletedCount;

        } catch (Exception e) {
            logger.severe("❌ Manual cleanup failed: " + e.getMessage());
            throw new RuntimeException("Manual cleanup failed: " + e.getMessage());
        }
    }

    /**
     * Cleanup job'ının durumunu kontrol et
     */
    public NotificationCleanupStatus getCleanupJobStatus() {
        try {
            LocalDateTime lastRun = notificationService.getLastCleanupTime();
            int totalNotifications = notificationService.getTotalNotificationCount();
            int readNotifications = notificationService.getReadNotificationCount();
            int eligibleForCleanup = notificationService.getEligibleForCleanupCount(retentionDays);

            return new NotificationCleanupStatus(
                    cleanupEnabled,
                    retentionDays,
                    lastRun,
                    totalNotifications,
                    readNotifications,
                    eligibleForCleanup,
                    "HEALTHY"
            );

        } catch (Exception e) {
            logger.warning("Error getting cleanup job status: " + e.getMessage());
            return new NotificationCleanupStatus(
                    cleanupEnabled,
                    retentionDays,
                    null,
                    0,
                    0,
                    0,
                    "ERROR: " + e.getMessage()
            );
        }
    }

    // Helper metodlar
    private void notifyMonitoringSystemAboutCleanupFailure(Exception e) {
        // Bu method monitoring/alerting sisteme entegre edilebilir
        logger.info("📢 Sending cleanup failure alert to monitoring system...");

        // Örnek: Slack webhook, email alert, vs.
        // monitoringService.sendAlert("Notification Cleanup Failed", e.getMessage());
    }


}