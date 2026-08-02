// OrderAutoService.java - Event publishing eklenmiş versiyon

package com.maxx_global.job;

import com.maxx_global.entity.Order;
import com.maxx_global.enums.OrderStatus;
import com.maxx_global.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Logger;

@Service
public class OrderAutoService {

    private static final Logger logger = Logger.getLogger(OrderAutoService.class.getName());

    // Configuration'dan çekilecek değerler
    @Value("${app.order.auto-cancel.enabled:true}")
    private Boolean autoCancelEnabled;

    @Value("${app.order.auto-cancel.hours:48}")
    private Integer autoCancelHours;

    @Value("${app.order.auto-cancel.batch-size:50}")
    private Integer batchSize;

    @Value("${app.order.auto-cancel.notify-admin:true}")
    private Boolean notifyAdmin;

    @Value("${app.order.auto-cancel.notify-customer:true}")
    private Boolean notifyCustomer;

    private final OrderRepository orderRepository;
    private final OrderAutoCancellationProcessor cancellationProcessor;

    public OrderAutoService(OrderRepository orderRepository,
                            OrderAutoCancellationProcessor cancellationProcessor) {
        this.orderRepository = orderRepository;
        this.cancellationProcessor = cancellationProcessor;
    }

    /**
     * Her 1 saatte bir çalışır ve süresi dolmuş siparişleri iptal eder
     * Cron expression configuration'dan alınabilir
     */
    @Scheduled(cron = "${app.order.auto-cancel.cron:0 0 */6 * * *}")
    public void autoCancel() {
        if (!autoCancelEnabled) {
            logger.info("🚫 Auto-cancel is disabled, skipping job");
            return;
        }

        logger.info("🔍 Starting auto-cancel job for pending approval orders...");
        logger.info("⚙️ Configuration: enabled=" + autoCancelEnabled +
                ", hours=" + autoCancelHours +
                ", batchSize=" + batchSize);

        try {
            // Cutoff time hesapla
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(autoCancelHours);

            // Süresi dolmuş siparişleri bul (batch size ile limit)
            List<Order> expiredOrders = orderRepository.findExpiredPendingApprovalOrders(cutoffTime);

            // Batch size kontrolü
            if (expiredOrders.size() > batchSize) {
                logger.info("⚠️ Found " + expiredOrders.size() + " orders, limiting to batch size: " + batchSize);
                expiredOrders = expiredOrders.subList(0, batchSize);
            }

            if (expiredOrders.isEmpty()) {
                logger.info("✅ No expired pending approval orders found");
                return;
            }

            logger.info("🚨 Found " + expiredOrders.size() + " expired pending approval orders");

            int successCount = 0;
            int failCount = 0;

            for (Order order : expiredOrders) {
                try {
                    int hoursWaited = calculateHoursWaited(order);
                    String cancellationReason = generateCancellationReason(order, hoursWaited);

                    boolean cancelled = cancellationProcessor.cancelExpiredOrder(
                            order.getId(), cancellationReason, hoursWaited, notifyAdmin || notifyCustomer);
                    if (cancelled) {
                        successCount++;
                        logger.info("✅ Auto-cancelled order: " + order.getOrderNumber()
                                + " (waited " + hoursWaited + " hours, amount="
                                + formatAmountForLog(order) + ")");
                    }

                } catch (Exception e) {
                    failCount++;
                    logger.severe("❌ Failed to auto-cancel order " + order.getOrderNumber() + ": " + e.getMessage());
                }
            }

            logger.info("🏁 Auto-cancel job completed: " + successCount + " successful, " + failCount + " failed");

            // Başarı oranı düşükse uyarı ver
            if (failCount > 0 && (failCount * 100 / expiredOrders.size()) > 20) {
                logger.warning("⚠️ High failure rate in auto-cancel job: " + failCount + "/" + expiredOrders.size());
            }

        } catch (Exception e) {
            logger.severe("💥 Auto-cancel job failed with error: " + e.getMessage());
            throw e; // Re-throw to let Spring handle the error
        }
    }

    private String generateCancellationReason(Order order, int hoursWaited) {
        return String.format("Müşteri %d saat (%d gün) içinde düzenlenen siparişi onaylamadığı için sistem tarafından otomatik olarak iptal edildi.",
                hoursWaited, hoursWaited / 24);
    }

    private int calculateHoursWaited(Order order) {
        return order.getUpdatedAt() == null ? autoCancelHours
                : (int) ChronoUnit.HOURS.between(order.getUpdatedAt(), LocalDateTime.now());
    }

    private String formatAmountForLog(Order order) {
        return order.getTotalAmount() == null ? "PRICE_UNAVAILABLE"
                : order.getTotalAmount().toPlainString() + " " + order.getCurrency();
    }

    /**
     * Manuel test için kullanılabilir
     */
    public void runAutoCancelJobManually() {
        logger.info("🔧 Running auto-cancel job manually (for testing)");
        logger.info("⚙️ Current config: enabled=" + autoCancelEnabled +
                ", hours=" + autoCancelHours +
                ", batchSize=" + batchSize);
        autoCancel();
    }

    /**
     * İstatistik bilgisi - kaç sipariş otomatik iptale aday
     */
    public int getPendingCancellationCount() {
        if (!autoCancelEnabled) {
            return 0;
        }

        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(autoCancelHours);
        List<Order> expiredOrders = orderRepository.findExpiredPendingApprovalOrders(cutoffTime);
        return expiredOrders.size();
    }

    /**
     * Belirli bir siparişin otomatik iptale ne kadar süre kaldığını hesaplar (saat cinsinden)
     */
    public long getHoursUntilAutoCancel(Order order) {
        if (!autoCancelEnabled) {
            return -1;
        }

        if (order.getOrderStatus() != OrderStatus.EDITED_PENDING_APPROVAL) {
            return -1; // Bu sipariş otomatik iptale tabi değil
        }

        LocalDateTime editTime = order.getUpdatedAt();
        if (editTime == null) {
            return -1;
        }

        LocalDateTime cancelTime = editTime.plusHours(autoCancelHours);
        LocalDateTime now = LocalDateTime.now();

        if (now.isAfter(cancelTime)) {
            return 0; // Süre dolmuş
        }

        return ChronoUnit.HOURS.between(now, cancelTime);
    }

    /**
     * Sistem sağlık kontrolü
     */
    public boolean isHealthy() {
        try {
            // DB bağlantısını test et
            long orderCount = orderRepository.count();

            // Configuration değerlerini kontrol et
            boolean configHealthy = autoCancelEnabled != null &&
                    autoCancelHours != null && autoCancelHours > 0 &&
                    batchSize != null && batchSize > 0;

            logger.info("🩺 Health check - Orders: " + orderCount + ", Config OK: " + configHealthy);
            return configHealthy;

        } catch (Exception e) {
            logger.severe("🩺 Health check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Detaylı sistem bilgileri
     */
    public Object getSystemInfo() {
        return new Object() {
            public final boolean enabled = autoCancelEnabled;
            public final int hours = autoCancelHours;
            public final int batchSize = OrderAutoService.this.batchSize;
            public final boolean notifyAdmin = OrderAutoService.this.notifyAdmin;
            public final boolean notifyCustomer = OrderAutoService.this.notifyCustomer;
            public final int pendingCancellations = getPendingCancellationCount();
            public final boolean isHealthy = isHealthy();
            public final String lastCheckTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
        };
    }
}
