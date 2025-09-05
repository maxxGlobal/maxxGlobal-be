package com.maxx_global.service;

import com.maxx_global.dto.notification.*;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.Notification;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.enums.NotificationStatus;
import com.maxx_global.enums.NotificationType;
import com.maxx_global.jobs.NotificationCleanupJob;
import com.maxx_global.repository.DealerRepository;
import com.maxx_global.repository.NotificationRepository;
import com.maxx_global.repository.AppUserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Service
@Transactional
public class NotificationService {

    private static final Logger logger = Logger.getLogger(NotificationService.class.getName());

    private final NotificationRepository notificationRepository;
    private final AppUserRepository appUserRepository;
    private final DealerRepository dealerRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               AppUserRepository appUserRepository, DealerRepository dealerRepository) {
        this.notificationRepository = notificationRepository;
        this.appUserRepository = appUserRepository;
        this.dealerRepository = dealerRepository;
    }

    /**
     * Yeni bildirim oluştur
     */
    @Transactional
    public List<NotificationResponse> createNotification(NotificationRequest request) {
        logger.info("Creating notification for dealer: " + request.dealerId());

        try {

            // Dealer'a bağlı aktif kullanıcıları getir
            List<AppUser> dealerUsers = appUserRepository.findByDealerIdAndStatusIs_Active(request.dealerId(),EntityStatus.ACTIVE);

            if (dealerUsers.isEmpty()) {
                logger.warning("No active users found for dealer: " + request.dealerId());
                return new ArrayList<>();
            }

            List<NotificationResponse> createdNotifications = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;

            for (AppUser user : dealerUsers) {
                try {
                    Notification notification = new Notification();
                    notification.setUser(user);
                    notification.setTitle(request.title());
                    notification.setMessage(request.message());
                    notification.setType(request.type());
                    notification.setNotificationStatus(NotificationStatus.UNREAD);
                    notification.setRelatedEntityId(request.relatedEntityId());
                    notification.setRelatedEntityType(request.relatedEntityType());
                    notification.setPriority(request.priority() != null ? request.priority() : "MEDIUM");
                    notification.setIcon(request.icon() != null ? request.icon() : getDefaultIcon(request.type()));
                    notification.setActionUrl(request.actionUrl());
                    notification.setData(request.data());

                    Notification saved = notificationRepository.save(notification);
                    createdNotifications.add(NotificationResponse.from(saved));
                    successCount++;

                    logger.info("Notification created for user: " + user.getId() + " (Dealer: " + request.dealerId() + ")");

                } catch (Exception e) {
                    failCount++;
                    logger.warning("Failed to create notification for user " + user.getId() +
                            " (Dealer: " + request.dealerId() + "): " + e.getMessage());
                }
            }

            logger.info("Dealer notification completed - Success: " + successCount +
                    ", Failed: " + failCount + ", Total: " + dealerUsers.size());

            return createdNotifications;

        } catch (Exception e) {
            logger.severe("Error creating dealer notification: " + e.getMessage());
            throw new RuntimeException("Dealer bildiimi oluşturulamadı: " + e.getMessage());
        }
    }

    /**
     * Kullanıcının bildirimlerini getir
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(Long userId, int page, int size,
                                                           NotificationFilterRequest filter) {
        logger.info("Fetching notifications for user: " + userId);

        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notifications;

        if (filter != null) {
            notifications = getFilteredNotifications(userId, filter, pageable);
        } else {
            notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        return notifications.map(NotificationResponse::from);
    }

    /**
     * Filtrelenmiş bildirimleri getir
     */
    private Page<Notification> getFilteredNotifications(Long userId, NotificationFilterRequest filter,
                                                        Pageable pageable) {
        if (filter.unreadOnly()) {
            return notificationRepository.findByUserIdAndNotificationStatusOrderByCreatedAtDesc(
                    userId, NotificationStatus.UNREAD, pageable);
        }

        if (filter.notificationStatus() != null) {
            return notificationRepository.findByUserIdAndNotificationStatusOrderByCreatedAtDesc(
                    userId, filter.notificationStatus(), pageable);
        }

        if (filter.type() != null) {
            return notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
                    userId, filter.type(), pageable);
        }

        if (filter.priority() != null) {
            return notificationRepository.findByUserIdAndPriorityOrderByCreatedAtDesc(
                    userId, filter.priority(), pageable);
        }

        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Bildirimi okunmuş olarak işaretle
     */
    public NotificationResponse markAsRead(Long notificationId, Long currentUserId) {
        logger.info("Marking notification as read: " + notificationId);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Bildirim bulunamadı: " + notificationId));

        // Güvenlik kontrolü
        if (!notification.getUser().getId().equals(currentUserId)) {
            throw new SecurityException("Bu bildirimi görme yetkiniz yok");
        }

        if (!notification.isRead()) {
            notification.markAsRead();
            notification = notificationRepository.save(notification);
        }

        return NotificationResponse.from(notification);
    }

    /**
     * Tüm bildirimleri okunmuş olarak işaretle
     */
    public void markAllAsRead(Long userId) {
        logger.info("Marking all notifications as read for user: " + userId);

        int updated = notificationRepository.markAllAsReadByUserId(
                userId,
                NotificationStatus.READ,
                LocalDateTime.now(),
                NotificationStatus.UNREAD
        );

        logger.info("Marked " + updated + " notifications as read");
    }

    /**
     * Bildirimi sil
     */
    public void deleteNotification(Long notificationId, Long currentUserId) {
        logger.info("Deleting notification: " + notificationId);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Bildirim bulunamadı: " + notificationId));

        // Güvenlik kontrolü
        if (!notification.getUser().getId().equals(currentUserId)) {
            throw new SecurityException("Bu bildirimi silme yetkiniz yok");
        }

        notificationRepository.delete(notification);
    }

    /**
     * Toplu durum güncelleme
     */
    public void bulkUpdateStatus(List<Long> notificationIds, Long userId,
                                 NotificationStatusUpdateRequest request) {
        logger.info("Bulk updating " + notificationIds.size() + " notifications");

        LocalDateTime readAt = (request.notificationStatus() == NotificationStatus.READ ||
                request.notificationStatus() == NotificationStatus.ARCHIVED)
                ? LocalDateTime.now() : null;

        int updated = notificationRepository.bulkUpdateStatus(
                notificationIds, userId, request.notificationStatus(), readAt);

        logger.info("Updated " + updated + " notifications");
    }

    /**
     * Kullanıcının bildirim özetini getir
     */
    @Transactional(readOnly = true)
    public NotificationSummary getUserNotificationSummary(Long userId) {
        logger.info("Getting notification summary for user: " + userId);

        long unreadCount = notificationRepository.countByUserIdAndNotificationStatus(
                userId, NotificationStatus.UNREAD);

        long readCount = notificationRepository.countByUserIdAndNotificationStatus(
                userId, NotificationStatus.READ);

        long archivedCount = notificationRepository.countByUserIdAndNotificationStatus(
                userId, NotificationStatus.ARCHIVED);

        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        long todayCount = notificationRepository.findRecentNotifications(userId, todayStart).size();

        LocalDateTime weekStart = LocalDateTime.now().minusDays(7);
        long thisWeekCount = notificationRepository.findRecentNotifications(userId, weekStart).size();

        // Yüksek öncelikli okunmamış bildirimler
        Page<Notification> highPriorityUnread = notificationRepository
                .findByUserIdAndPriorityOrderByCreatedAtDesc(userId, "HIGH", PageRequest.of(0, 100));
        long highPriorityUnreadCount = highPriorityUnread.getTotalElements();

        return new NotificationSummary(
                unreadCount + readCount + archivedCount,
                unreadCount,
                readCount,
                archivedCount,
                todayCount,
                thisWeekCount,
                highPriorityUnreadCount
        );
    }

    /**
     * Tüm aktif kullanıcılara bildirim gönder
     */
    @Transactional
    public List<NotificationResponse> createNotificationForAllUsers(NotificationBroadcastRequest request) {
        logger.info("Creating notification for all users: " + request.title());

        try {
            // Tüm aktif kullanıcıları getir
            List<AppUser> activeUsers = appUserRepository.findByStatus(EntityStatus.ACTIVE);

            if (activeUsers.isEmpty()) {
                logger.warning("No active users found for broadcast notification");
                return new ArrayList<>();
            }


            for (AppUser user : activeUsers) {
                try {
                    NotificationRequest notificationRequest = new NotificationRequest(
                            user.getId(),
                            request.title(),
                            request.message(),
                            request.type(),
                            request.relatedEntityId(),
                            request.relatedEntityType(),
                            request.priority() != null ? request.priority() : "MEDIUM",
                            request.icon() != null ? request.icon() : getDefaultIcon(request.type()),
                            request.actionUrl(),
                            request.data()
                    );

                     createNotification(notificationRequest);


                } catch (Exception e) {
                    logger.warning("Failed to create notification for user " + user.getId() + ": " + e.getMessage());
                    // Bir kullanıcı için hata olursa diğerlerine devam et
                }
            }

            logger.info("Broadcast notification created for "  + "/" + activeUsers.size() + " users");
            return new ArrayList<>();

        } catch (Exception e) {
            logger.severe("Error creating broadcast notification: " + e.getMessage());
            throw new RuntimeException("Toplu bildirim oluşturulamadı: " + e.getMessage());
        }
    }

    /**
     * Belirli roller/yetkilere sahip kullanıcılara bildirim gönder
     */
    @Transactional
    public List<NotificationResponse> createNotificationForUsersByRole(String roleName, NotificationBroadcastRequest request) {
        logger.info("Creating notification for users with role: " + roleName);

        try {
            // Belirli role sahip kullanıcıları getir
            List<AppUser> usersWithRole = appUserRepository.findActiveUsersByRoleName(roleName);

            if (usersWithRole.isEmpty()) {
                logger.warning("No users found with role: " + roleName);
                return new ArrayList<>();
            }

            List<NotificationResponse> createdNotifications = new ArrayList<>();

            for (AppUser user : usersWithRole) {
                try {
                    NotificationRequest notificationRequest = new NotificationRequest(
                            user.getId(),
                            request.title(),
                            request.message(),
                            request.type(),
                            request.relatedEntityId(),
                            request.relatedEntityType(),
                            request.priority() != null ? request.priority() : "MEDIUM",
                            request.icon() != null ? request.icon() : getDefaultIcon(request.type()),
                            request.actionUrl(),
                            request.data()
                    );

                    NotificationResponse notification = createNotificationForUser(notificationRequest, user);
                    createdNotifications.add(notification);

                } catch (Exception e) {
                    logger.warning("Failed to create notification for user " + user.getId() + ": " + e.getMessage());
                }
            }

            logger.info("Role-based notification created for " + createdNotifications.size() + "/" + usersWithRole.size() + " users");
            return createdNotifications;

        } catch (Exception e) {
            logger.severe("Error creating role-based notification: " + e.getMessage());
            throw new RuntimeException("Role bazlı bildirim oluşturulamadı: " + e.getMessage());
        }
    }

    public NotificationResponse createNotificationForUser(NotificationRequest request,AppUser user) {
        logger.info("Creating notification for user: " + user);


        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(request.title());
        notification.setMessage(request.message());
        notification.setType(request.type());
        notification.setNotificationStatus(NotificationStatus.UNREAD);
        notification.setRelatedEntityId(request.relatedEntityId());
        notification.setRelatedEntityType(request.relatedEntityType());
        notification.setPriority(request.priority() != null ? request.priority() : "MEDIUM");
        notification.setIcon(request.icon() != null ? request.icon() : getDefaultIcon(request.type()));
        notification.setActionUrl(request.actionUrl());
        notification.setData(request.data());

        Notification saved = notificationRepository.save(notification);
        logger.info("Notification created with ID: " + saved.getId());

        return NotificationResponse.from(saved);
    }

    /**
     * Belirli kullanıcı listesine bildirim gönder
     */
    @Transactional
    public List<NotificationResponse> createNotificationForSpecificUsers(List<Long> userIds, NotificationBroadcastRequest request) {
        logger.info("Creating notification for specific users: " + userIds.size());

        try {
            if (userIds.isEmpty()) {
                throw new IllegalArgumentException("En az bir kullanıcı ID'si gereklidir");
            }

            List<NotificationResponse> createdNotifications = new ArrayList<>();
            List<Long> failedUserIds = new ArrayList<>();

            for (Long userId : userIds) {
                try {
                    // Kullanıcı varlık kontrolü
                    if (!appUserRepository.existsById(userId)) {
                        logger.warning("User not found: " + userId);
                        failedUserIds.add(userId);
                        continue;
                    }

                    NotificationRequest notificationRequest = new NotificationRequest(
                            userId,
                            request.title(),
                            request.message(),
                            request.type(),
                            request.relatedEntityId(),
                            request.relatedEntityType(),
                            request.priority() != null ? request.priority() : "MEDIUM",
                            request.icon() != null ? request.icon() : getDefaultIcon(request.type()),
                            request.actionUrl(),
                            request.data()
                    );

                    NotificationResponse notification = createNotificationForUser(notificationRequest, appUserRepository.findById(userId).get());
                    createdNotifications.add(notification);

                } catch (Exception e) {
                    logger.warning("Failed to create notification for user " + userId + ": " + e.getMessage());
                    failedUserIds.add(userId);
                }
            }

            logger.info("Specific users notification created for " + createdNotifications.size() + "/" + userIds.size() + " users");

            if (!failedUserIds.isEmpty()) {
                logger.warning("Failed to create notifications for users: " + failedUserIds);
            }

            return createdNotifications;

        } catch (Exception e) {
            logger.severe("Error creating specific users notification: " + e.getMessage());
            throw new RuntimeException("Belirli kullanıcı bildiimi oluşturulamadı: " + e.getMessage());
        }
    }

    /**
     * Bildirim gönderim istatistiklerini getir
     */
    @Transactional(readOnly = true)
    public NotificationBroadcastStatsResponse getBroadcastStats(String timeRange) {
        logger.info("Getting broadcast notification stats for: " + timeRange);

        try {
            LocalDateTime startDate = switch (timeRange.toLowerCase()) {
                case "today" -> LocalDateTime.now().toLocalDate().atStartOfDay();
                case "week" -> LocalDateTime.now().minusWeeks(1);
                case "month" -> LocalDateTime.now().minusMonths(1);
                default -> LocalDateTime.now().minusDays(7);
            };

            // İstatistikleri hesapla
            long totalNotifications = notificationRepository.countByCreatedAtAfter(startDate);
            long totalUsers = appUserRepository.countByStatus(EntityStatus.ACTIVE);

            Map<NotificationType, Long> notificationsByType = notificationRepository
                    .countNotificationsByTypeAfter(startDate);

            Map<String, Long> notificationsByPriority = notificationRepository
                    .countNotificationsByPriorityAfter(startDate);

            double readRate = calculateReadRate(startDate);

            return new NotificationBroadcastStatsResponse(
                    totalNotifications,
                    totalUsers,
                    notificationsByType,
                    notificationsByPriority,
                    readRate,
                    startDate,
                    LocalDateTime.now()
            );

        } catch (Exception e) {
            logger.severe("Error getting broadcast stats: " + e.getMessage());
            throw new RuntimeException("İstatistikler alınamadı: " + e.getMessage());
        }
    }

    // Helper method
    private double calculateReadRate(LocalDateTime startDate) {
        try {
            long totalNotifications = notificationRepository.countByCreatedAtAfter(startDate);
            long readNotifications = notificationRepository.countByCreatedAtAfterAndNotificationStatus(
                    startDate, NotificationStatus.READ);

            return totalNotifications > 0 ? (double) readNotifications / totalNotifications * 100 : 0.0;
        } catch (Exception e) {
            logger.warning("Error calculating read rate: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * Son bildirimleri getir (header için)
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getRecentNotifications(Long userId, int limit) {
        List<Notification> notifications = notificationRepository
                .findTopNotificationsByUserId(userId, limit);

        return notifications.stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /**
     * Okunmamış bildirim sayısı
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndNotificationStatus(
                userId, NotificationStatus.UNREAD);
    }

    /**
     * Default ikon belirle
     */
    private String getDefaultIcon(NotificationType type) {
        return switch (type.getCategory()) {
            case "order" -> "shopping-cart";
            case "system" -> "settings";
            case "product" -> "package";
            case "user" -> "user";
            case "promotion" -> "percent";
            default -> "bell";
        };
    }

    /**
     * Eski bildirimleri temizle (scheduled job için)
     */
    public void cleanupOldNotifications(int retentionDays) {
        logger.info("Cleaning up notifications older than " + retentionDays + " days");

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        int deleted = notificationRepository.deleteOldNotifications(cutoffDate);

        logger.info("Deleted " + deleted + " old notifications");
    }

    // NotificationService.java içine eklenecek metodlar:

    /**
     * Okunmuş bildirimleri temizle (20 gün sonra)
     */
    @Transactional
    public int cleanupReadNotifications(int retentionDays) {
        logger.info("Starting cleanup of read notifications older than " + retentionDays + " days");

        try {
            // Cutoff tarihini hesapla
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);

            // Sadece READ ve ARCHIVED durumundaki eski bildirimleri sil
            List<NotificationStatus> notificationStatuses = new ArrayList<>();
            notificationStatuses.add(NotificationStatus.READ);
            notificationStatuses.add(NotificationStatus.ARCHIVED);

            int deletedCount = notificationRepository.deleteReadNotificationsOlderThan(cutoffDate,notificationStatuses);

            logger.info("Cleanup completed - deleted " + deletedCount + " read notifications older than " +
                    cutoffDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

            return deletedCount;

        } catch (Exception e) {
            logger.severe("Error during notification cleanup: " + e.getMessage());
            throw new RuntimeException("Notification cleanup failed: " + e.getMessage());
        }
    }

    /**
     * Belirli kullanıcının eski okunmuş bildirimlerini temizle
     */
    @Transactional
    public int cleanupUserReadNotifications(Long userId, int retentionDays) {
        logger.info("Cleaning up read notifications for user: " + userId);

        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
            List<NotificationStatus> notificationStatuses = new ArrayList<>();
            notificationStatuses.add(NotificationStatus.READ);
            notificationStatuses.add(NotificationStatus.ARCHIVED);
            int deletedCount = notificationRepository.deleteUserReadNotificationsOlderThan(userId, cutoffDate,notificationStatuses);

            logger.info("User cleanup completed - deleted " + deletedCount + " notifications for user: " + userId);

            return deletedCount;

        } catch (Exception e) {
            logger.severe("Error during user notification cleanup: " + e.getMessage());
            throw new RuntimeException("User notification cleanup failed: " + e.getMessage());
        }
    }

    /**
     * Cleanup için uygun notification sayısını getir
     */
    @Transactional(readOnly = true)
    public int getEligibleForCleanupCount(int retentionDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        List<NotificationStatus> notificationStatuses = new ArrayList<>();
        notificationStatuses.add(NotificationStatus.READ);
        notificationStatuses.add(NotificationStatus.ARCHIVED);
        return notificationRepository.countReadNotificationsOlderThan(cutoffDate,notificationStatuses);
    }

    /**
     * Toplam notification sayısını getir
     */
    @Transactional(readOnly = true)
    public int getTotalNotificationCount() {
        return (int) notificationRepository.count();
    }

    /**
     * Okunmuş notification sayısını getir
     */
    @Transactional(readOnly = true)
    public int getReadNotificationCount() {
        return notificationRepository.countByNotificationStatusIn(
                List.of(NotificationStatus.READ, NotificationStatus.ARCHIVED)
        );
    }

    /**
     * Son hafta cleanup istatistiklerini getir
     */
    @Transactional(readOnly = true)
    public NotificationCleanupStats getCleanupStatsForLastWeek() {
        LocalDateTime weekStart = LocalDateTime.now().minusWeeks(1);
        LocalDateTime weekEnd = LocalDateTime.now();

        // Bu bilgi cache'den veya log'dan alınabilir
        // Şimdilik basit bir hesaplama
        int estimatedCleaned = 50; // Geçici değer

        return new NotificationCleanupStats(
                estimatedCleaned,
                weekStart,
                weekEnd,
                estimatedCleaned / 7.0
        );
    }

    /**
     * Cleanup işlemi güvenli mi kontrol et
     */
    @Transactional(readOnly = true)
    public boolean isCleanupSafe(int retentionDays) {
        try {
            // Sistem yükü kontrolleri
            int totalNotifications = getTotalNotificationCount();
            int eligibleForCleanup = getEligibleForCleanupCount(retentionDays);

            // Eğer sistemde çok fazla notification varsa ve cleanup oranı yüksekse dikkatli ol
            if (totalNotifications > 100000 && eligibleForCleanup > totalNotifications * 0.5) {
                logger.warning("Large cleanup detected - total: " + totalNotifications +
                        ", eligible: " + eligibleForCleanup);
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.warning("Error checking cleanup safety: " + e.getMessage());
            return false;
        }
    }

    /**
     * Emergency cleanup - kritik durumlarda kullanılır
     */
    @Transactional
    public int emergencyCleanup(int aggressiveRetentionDays) {
        logger.warning("🚨 EMERGENCY CLEANUP TRIGGERED - retention: " + aggressiveRetentionDays + " days");

        try {
            // Daha agresif cleanup
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(aggressiveRetentionDays);

            // Tüm eski bildirimleri sil (okunmuş/okunmamış fark etmez)
            int deletedCount = notificationRepository.deleteAllNotificationsOlderThan(cutoffDate);

            logger.warning("🧹 Emergency cleanup completed - deleted: " + deletedCount + " notifications");

            return deletedCount;

        } catch (Exception e) {
            logger.severe("❌ Emergency cleanup failed: " + e.getMessage());
            throw new RuntimeException("Emergency cleanup failed: " + e.getMessage());
        }
    }

    /**
     * Cleanup preview - silmeden önce ne kadar silinecek göster
     */
    @Transactional(readOnly = true)
    public NotificationCleanupPreview previewCleanup(int retentionDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);

        List<NotificationStatus> notificationStatuses = new ArrayList<>();
        notificationStatuses.add(NotificationStatus.READ);
        notificationStatuses.add(NotificationStatus.ARCHIVED);

        int readNotifications = notificationRepository.countReadNotificationsOlderThan(cutoffDate,notificationStatuses);
        int archivedNotifications = notificationRepository.countArchivedNotificationsOlderThan(cutoffDate,NotificationStatus.ARCHIVED);
        int totalEligible = readNotifications + archivedNotifications;

        // Kullanıcı bazında breakdown
        Map<String, Integer> userBreakdown = notificationRepository.getCleanupBreakdownByUser(cutoffDate);

        return new NotificationCleanupPreview(
                cutoffDate,
                retentionDays,
                totalEligible,
                readNotifications,
                archivedNotifications,
                userBreakdown,
                calculateEstimatedSpaceSaving(totalEligible)
        );
    }

    // Helper metodlar
    private long calculateEstimatedSpaceSaving(int notificationCount) {
        // Ortalama bir notification ~2KB yer kaplar (tahmini)
        return notificationCount * 2L; // KB cinsinden
    }

}