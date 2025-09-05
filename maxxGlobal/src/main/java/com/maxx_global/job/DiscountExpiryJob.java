package com.maxx_global.job;

import com.maxx_global.entity.Discount;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.event.DiscountExpiredEvent;
import com.maxx_global.event.DiscountSoonExpiringEvent;
import com.maxx_global.repository.DiscountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Logger;

@Component
public class DiscountExpiryJob {

    private static final Logger logger = Logger.getLogger(DiscountExpiryJob.class.getName());

    private final DiscountRepository discountRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.discount.expiry-warning-days:3}")
    private int expiryWarningDays;

    @Value("${app.discount.expiry-check.enabled:true}")
    private boolean expiryCheckEnabled;

    public DiscountExpiryJob(DiscountRepository discountRepository,
                             ApplicationEventPublisher eventPublisher) {
        this.discountRepository = discountRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Her gün saat 09:00'da çalışır - Süresi dolan indirimleri kontrol eder
     */
    @Scheduled(cron = "0 0 9 * * ?") // Her gün 09:00
    public void checkExpiredDiscounts() {
        if (!expiryCheckEnabled) {
            logger.info("📤 Discount expiry check is disabled, skipping job");
            return;
        }

        logger.info("🕒 Starting discount expiry check job...");

        try {
            // Süresi dolan aktif indirimleri bul
            List<Discount> expiredDiscounts = discountRepository.findExpiredDiscounts(EntityStatus.ACTIVE);

            if (expiredDiscounts.isEmpty()) {
                logger.info("✅ No expired discounts found");
            } else {
                logger.info("🚨 Found " + expiredDiscounts.size() + " expired discounts");

                for (Discount discount : expiredDiscounts) {
                    try {
                        // İndirimi pasif yap
                        discount.setIsActive(false);
                        discountRepository.save(discount);

                        // Event publish et
                        DiscountExpiredEvent event = new DiscountExpiredEvent(discount);
                        eventPublisher.publishEvent(event);

                        logger.info("✅ Processed expired discount: " + discount.getName() +
                                " (expired on: " + discount.getEndDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + ")");

                    } catch (Exception e) {
                        logger.severe("❌ Failed to process expired discount " + discount.getName() + ": " + e.getMessage());
                    }
                }

                logger.info("🏁 Expired discount processing completed: " + expiredDiscounts.size() + " discounts processed");
            }

        } catch (Exception e) {
            logger.severe("💥 Discount expiry job failed: " + e.getMessage());
        }
    }

    /**
     * Her gün saat 10:00'da çalışır - Yakında süresi dolacak indirimleri kontrol eder
     */
    @Scheduled(cron = "0 0 10 * * ?") // Her gün 10:00
    public void checkSoonExpiringDiscounts() {
        if (!expiryCheckEnabled) {
            logger.info("📤 Discount soon expiring check is disabled, skipping job");
            return;
        }

        logger.info("⚠️ Starting soon expiring discounts check job...");

        try {
            // Yakında süresi dolacak indirimleri bul (örn: 3 gün içinde)
            List<Discount> soonExpiringDiscounts = discountRepository.findDiscountsExpiringInDays(
                    expiryWarningDays, EntityStatus.ACTIVE);

            if (soonExpiringDiscounts.isEmpty()) {
                logger.info("✅ No soon expiring discounts found");
            } else {
                logger.info("⚠️ Found " + soonExpiringDiscounts.size() + " soon expiring discounts");

                for (Discount discount : soonExpiringDiscounts) {
                    try {
                        // Kaç gün kaldığını hesapla
                        int daysUntilExpiration = (int) ChronoUnit.DAYS.between(
                                LocalDateTime.now().toLocalDate(),
                                discount.getEndDate().toLocalDate()
                        );

                        // Sadece belirli günlerde uyarı gönder (1, 2, 3 gün kala)
                        if (daysUntilExpiration <= expiryWarningDays && daysUntilExpiration > 0) {
                            // Event publish et
                            DiscountSoonExpiringEvent event = new DiscountSoonExpiringEvent(
                                    discount, daysUntilExpiration);
                            eventPublisher.publishEvent(event);

                            logger.info("⚠️ Soon expiring notification sent for: " + discount.getName() +
                                    " (expires in " + daysUntilExpiration + " days)");
                        }

                    } catch (Exception e) {
                        logger.severe("❌ Failed to process soon expiring discount " + discount.getName() + ": " + e.getMessage());
                    }
                }

                logger.info("🏁 Soon expiring discount processing completed");
            }

        } catch (Exception e) {
            logger.severe("💥 Soon expiring discount job failed: " + e.getMessage());
        }
    }

    /**
     * Manuel test için kullanılabilir
     */
    public void runExpiryCheckManually() {
        logger.info("🔧 Running discount expiry check manually (for testing)");
        logger.info("⚙️ Current config: enabled=" + expiryCheckEnabled +
                ", warning days=" + expiryWarningDays);

        checkExpiredDiscounts();
        checkSoonExpiringDiscounts();
    }

    /**
     * Sistem durumu kontrolü
     */
    public Object getExpiryJobStatus() {
        try {
            long activeDiscounts = discountRepository.countActiveDiscounts(EntityStatus.ACTIVE);
            List<Discount> expiredDiscounts = discountRepository.findExpiredDiscounts(EntityStatus.ACTIVE);
            List<Discount> soonExpiring = discountRepository.findDiscountsExpiringInDays(
                    expiryWarningDays, EntityStatus.ACTIVE);

            return new Object() {
                public final boolean enabled = expiryCheckEnabled;
                public final int warningDays = expiryWarningDays;
                public final long totalActiveDiscounts = activeDiscounts;
                public final int expiredDiscountsCount = expiredDiscounts.size();
                public final int soonExpiringCount = soonExpiring.size();
                public final String lastCheckTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
                public final boolean isHealthy = true;
            };

        } catch (Exception e) {
            logger.severe("Error getting expiry job status: " + e.getMessage());
            return new Object() {
                public final boolean enabled = expiryCheckEnabled;
                public final String error = e.getMessage();
                public final boolean isHealthy = false;
            };
        }
    }

    /**
     * İstatistik bilgileri
     */
    public Object getExpiryStatistics() {
        try {
            // Bu ay süresi dolan indirimler
            long thisMonthExpired = discountRepository.countDiscountsCreatedThisMonth(EntityStatus.DELETED);

            // Aktif indirimler
            long activeDiscounts = discountRepository.countActiveDiscounts(EntityStatus.ACTIVE);

            // Yakında dolacak indirimler
            List<Discount> soonExpiring = discountRepository.findDiscountsExpiringInDays(
                    expiryWarningDays, EntityStatus.ACTIVE);

            return new Object() {
                public final long activeDiscounts = DiscountExpiryJob.this.discountRepository.countActiveDiscounts(EntityStatus.ACTIVE);
                public final long thisMonthExpiredCount = thisMonthExpired;
                public final int soonExpiringCount = soonExpiring.size();
                public final int warningDays = expiryWarningDays;
                public final String reportDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            };

        } catch (Exception e) {
            logger.warning("Error getting expiry statistics: " + e.getMessage());
            return new Object() {
                public final String error = e.getMessage();
            };
        }
    }
}