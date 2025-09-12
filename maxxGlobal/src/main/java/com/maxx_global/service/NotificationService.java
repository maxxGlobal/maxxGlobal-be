package com.maxx_global.service;

import com.maxx_global.dto.dealer.DealerValidationResult;
import com.maxx_global.dto.notification.*;
import com.maxx_global.entity.AppUser;
import com.maxx_global.entity.Dealer;
import com.maxx_global.entity.Notification;
import com.maxx_global.enums.EntityStatus;
import com.maxx_global.enums.NotificationStatus;
import com.maxx_global.enums.NotificationType;
import com.maxx_global.repository.DealerRepository;
import com.maxx_global.repository.NotificationRepository;
import com.maxx_global.repository.AppUserRepository;
import com.maxx_global.security.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

    public List<NotificationResponse> createNotification(NotificationRequest request) {
        return createNotification(request, false,null);
    }

    public List<NotificationResponse> createNotification(NotificationRequest request,List<AppUser> users) {
        return createNotification(request, false, users);
    }

    public void createNotificationByEvent(NotificationRequest request) {
        AppUser user = appUserRepository.findById(request.dealerId()).orElseThrow(() -> new EntityNotFoundException("Kullanıcı bulunamadı: " + request.dealerId()));
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

        notificationRepository.save(notification);
    }

    public void createNotificationForAdminByEvent(NotificationRequest request, List<AppUser> users) {
        for (AppUser user : users) {
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

            } catch (Exception e) {
                logger.warning("Failed to create notification for user " + user.getId() +
                        " (Dealer: " + request.dealerId() + "): " + e.getMessage());
            }
        }
    }

    /**
     * Yeni bildirim oluştur
     */
    @Transactional
    public List<NotificationResponse> createNotification(NotificationRequest request,Boolean sentAll,List<AppUser> usersForNotification) {
        logger.info("Creating notification for dealer: " + request.dealerId());

        try {
            List<AppUser> users ;
            if(!usersForNotification.isEmpty()){
                users = usersForNotification;
            }else{
                // Dealer'a bağlı aktif kullanıcıları getir
                users = appUserRepository.findByDealerIdAndStatusIs_Active(request.dealerId(),EntityStatus.ACTIVE);
            }

            if (users.isEmpty()) {
                logger.warning("No active users found for dealer: " + request.dealerId());
                return new ArrayList<>();
            }

            List<NotificationResponse> createdNotifications = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;

            for (AppUser user : users) {
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
                    ", Failed: " + failCount + ", Total: " + users.size());

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

        // Güvenlik kontrolü - ya notification sahibi ya da NOTIFICATION_MANAGEMENT yetkisi olan
        if (!notification.getUser().getId().equals(currentUserId) &&
                !hasNotificationManagementAuthority()) {
            throw new SecurityException("Bu bildirimi silme yetkiniz yok");
        }

        notificationRepository.delete(notification);
    }

    private boolean hasNotificationManagementAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("NOTIFICATION_MANAGEMENT"));
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
    /**
     * Tüm aktif kullanıcılara bildirim gönder
     */
    @Transactional
    public List<NotificationResponse> createNotificationForAllUsers(NotificationBroadcastRequest request) {
        logger.info("Creating notification for all users: " + request.title());

        try {
            // Mevcut kullanıcıyı al (bildirimi oluşturan)
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();



            // Tüm aktif kullanıcıları getir
            List<AppUser> activeUsers = appUserRepository.findByStatus(EntityStatus.ACTIVE);

            if (activeUsers.isEmpty()) {
                logger.warning("No active users found for broadcast notification");
                return new ArrayList<>();
            }



            logger.info("Sending notification to " + activeUsers.size());

            List<NotificationResponse> createdNotifications = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;

            // Her kullanıcı için TEK BİLDİRİM oluştur
            for (AppUser user : activeUsers) {
                if (principal instanceof CustomUserDetails customUser) {
                    if (user.getId().equals(customUser.getId())) {
                        continue;
                    }
                }
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

                    logger.info("Notification created for user: " + user.getId());

                } catch (Exception e) {
                    failCount++;
                    logger.warning("Failed to create notification for user " + user.getId() + ": " + e.getMessage());
                }
            }

            logger.info("Broadcast notification completed - Success: " + successCount +
                    ", Failed: " + failCount + ", Target users: " + activeUsers.size() );

            return createdNotifications;

        } catch (Exception e) {
            logger.severe("Error creating broadcast notification: " + e.getMessage());
            throw new RuntimeException("Toplu bildirim oluşturulamadı: " + e.getMessage());
        }
    }

    @Transactional
    public List<NotificationResponse> createNotificationForMultipleDealers(List<Long> dealerIds, NotificationBroadcastRequest request) {
        logger.info("Creating notification for multiple dealers: " + dealerIds.size() + " dealers");

        try {
            if (dealerIds == null || dealerIds.isEmpty()) {
                throw new IllegalArgumentException("En az bir dealer ID'si gereklidir");
            }

            List<NotificationResponse> allCreatedNotifications = new ArrayList<>();
            List<Long> successfulDealers = new ArrayList<>();
            List<Long> failedDealers = new ArrayList<>();

            for (Long dealerId : dealerIds) {
                try {
                    // Dealer varlık kontrolü
                    dealerRepository.findById(dealerId)
                            .orElseThrow(() -> new EntityNotFoundException("Dealer bulunamadı: " + dealerId));

                    // Bu dealer için NotificationRequest oluştur
                    NotificationRequest dealerRequest = new NotificationRequest(
                            dealerId,
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

                    // Bu dealer'a bağlı kullanıcılara bildirim gönder
                    List<NotificationResponse> dealerNotifications = createNotification(dealerRequest);
                    allCreatedNotifications.addAll(dealerNotifications);
                    successfulDealers.add(dealerId);

                    logger.info("Notifications sent to dealer " + dealerId + ": " + dealerNotifications.size() + " users");

                } catch (Exception e) {
                    logger.warning("Failed to send notification to dealer " + dealerId + ": " + e.getMessage());
                    failedDealers.add(dealerId);
                }
            }

            logger.info("Multi-dealer notification completed - " +
                    "Success: " + successfulDealers.size() + " dealers, " +
                    "Failed: " + failedDealers.size() + " dealers, " +
                    "Total notifications: " + allCreatedNotifications.size());

            if (!failedDealers.isEmpty()) {
                logger.warning("Failed dealers: " + failedDealers);
            }

            return allCreatedNotifications;

        } catch (Exception e) {
            logger.severe("Error creating multi-dealer notification: " + e.getMessage());
            throw new RuntimeException("Çoklu dealer bildiimi oluşturulamadı: " + e.getMessage());
        }
    }

    /**
     * Dealer bilgilerini toplu olarak doğrula
     */
    @Transactional(readOnly = true)
    public List<DealerValidationResult> validateDealers(List<Long> dealerIds) {
        logger.info("Validating dealers: " + dealerIds);

        return dealerIds.stream()
                .map(dealerId -> {
                    try {
                        Dealer dealer = dealerRepository.findById(dealerId).orElse(null);
                        if (dealer == null) {
                            return new DealerValidationResult(dealerId, false, "Dealer bulunamadı", 0);
                        }

                        int userCount = appUserRepository.findByDealerIdAndStatusIs_Active(dealerId, EntityStatus.ACTIVE).size();
                        return new DealerValidationResult(dealerId, true, dealer.getName(), userCount);

                    } catch (Exception e) {
                        return new DealerValidationResult(dealerId, false, "Hata: " + e.getMessage(), 0);
                    }
                })
                .toList();
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

    // NotificationService.java'ya eklenecek metotlar:

    /**
     * Admin tarafından gönderilen bildirimleri listele
     */
    @Transactional(readOnly = true)
    public Page<AdminNotificationResponse> getAdminSentNotifications(int page, int size, AdminNotificationFilter filter) {
        logger.info("Fetching admin sent notifications");

        try {
            // Sort'u kaldır, query'de zaten ORDER BY var
            Pageable pageable = PageRequest.of(page, size);

            Page<Object[]> distinctNotifications = notificationRepository.findDistinctAdminNotifications(pageable);

            List<AdminNotificationResponse> responses = distinctNotifications.getContent().stream()
                    .map(this::convertObjectArrayToAdminResponse)
                    .collect(Collectors.toList());

            return new PageImpl<>(responses, pageable, distinctNotifications.getTotalElements());

        } catch (Exception e) {
            logger.severe("Error fetching admin sent notifications: " + e.getMessage());
            throw new RuntimeException("Admin bildirimleri alınamadı: " + e.getMessage());
        }
    }

    private AdminNotificationResponse convertObjectArrayToAdminResponse(Object[] row) {
        int totalRecipients = ((Number) row[6]).intValue();
        int readCount = ((Number) row[8]).intValue();
        int unreadCount = totalRecipients - readCount;
        double readPercentage = totalRecipients > 0 ? (readCount * 100.0) / totalRecipients : 0.0;

        return new AdminNotificationResponse(
                ((Number) row[0]).longValue(),              // id
                (String) row[1],                            // title
                (String) row[2],                            // message
                NotificationType.valueOf((String) row[3]), // type
                NotificationType.valueOf((String) row[3]).getDisplayName(), // typeDisplayName
                "NORMAL",                                   // priority - default
                "bell",                                     // icon - default
                ((Timestamp) row[4]).toLocalDateTime(),     // createdAt

                totalRecipients,                            // totalRecipients
                readCount,                                  // readCount
                unreadCount,                                // unreadCount
                Math.round(readPercentage * 100.0) / 100.0, // readPercentage (2 decimal places)

                "ALL_USERS",                                // broadcastType
                "Tüm Kullanıcılar",                        // targetInfo

                row[9] != null ? ((Timestamp) row[9]).toLocalDateTime() : null // lastReadAt
        );
    }

    /**
     * Basit admin response'a çevir
     */
    private AdminNotificationResponse convertToSimpleAdminResponse(Notification notification) {
        // Aynı başlık/mesaj/tip ile kaç tane bildirim var say - String parametreler
        long sameNotificationCount = notificationRepository.countSameNotifications(
                notification.getTitle(),
                notification.getMessage(),
                notification.getType());

        // Kaç tanesi okunmuş say - String parametreler
        long readCount = notificationRepository.countReadSameNotifications(
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                Arrays.asList(NotificationStatus.READ,NotificationStatus.ARCHIVED));

        int totalRecipients = (int) sameNotificationCount;
        int readCountInt = (int) readCount;
        int unreadCount = totalRecipients - readCountInt;
        double readPercentage = totalRecipients > 0 ? (double) readCountInt / totalRecipients * 100 : 0.0;

        return new AdminNotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                notification.getType().getDisplayName(),
                notification.getPriority(),
                notification.getIcon(),
                notification.getCreatedAt(),
                totalRecipients,
                readCountInt,
                unreadCount,
                readPercentage,
                "ADMIN_CREATED",
                totalRecipients + " kullanıcı",
                notification.getReadAt()
        );
    }

    // Helper metodlar
    private NotificationType parseNotificationType(String type) {
        if (type == null || type.isBlank()) return null;
        try {
            return NotificationType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid notification type: " + type);
            return null;
        }
    }

    private LocalDateTime parseDate(String dateStr, boolean isStartOfDay) {
        if (dateStr == null || dateStr.isBlank()) return null;

        try {
            return LocalDateTime.parse(dateStr + (isStartOfDay ? "T00:00:00" : "T23:59:59"));
        } catch (Exception e) {
            logger.warning("Invalid date format: " + dateStr);
            return null;
        }
    }

    private boolean hasAnyFilter(AdminNotificationFilter filter) {
        if (filter == null) return false;

        return (filter.type() != null && !filter.type().isBlank()) ||
                (filter.priority() != null && !filter.priority().isBlank()) ||
                (filter.startDate() != null && !filter.startDate().isBlank()) ||
                (filter.endDate() != null && !filter.endDate().isBlank()) ||
                (filter.searchTerm() != null && !filter.searchTerm().isBlank());
    }
    /**
     * Belirli bir bildirimin detaylarını getir
     */
    @Transactional(readOnly = true)
    public AdminNotificationDetailResponse getNotificationDetails(Long notificationId) {
        logger.info("Fetching notification details for: " + notificationId);

        try {
            // Ana bildirimi bul
            Notification notification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> new EntityNotFoundException("Bildirim bulunamadı: " + notificationId));

            // Aynı başlık ve mesaja sahip tüm bildirimleri bul (aynı broadcast'e ait)
            List<Notification> relatedNotifications = findRelatedNotifications(notification);

            // İstatistikleri hesapla
            return buildNotificationDetails(notification, relatedNotifications);

        } catch (Exception e) {
            logger.severe("Error fetching notification details: " + e.getMessage());
            throw new RuntimeException("Bildirim detayları alınamadı: " + e.getMessage());
        }
    }



// ==================== HELPER METODLAR ====================

    private LocalDateTime calculateStartDate(String timeRange) {
        if (timeRange == null || timeRange.trim().isEmpty()) {
            return LocalDateTime.now().minusMonths(1); // Varsayılan: 1 ay
        }

        return switch (timeRange.toLowerCase().trim()) {
            case "today", "bugün" -> LocalDateTime.now().toLocalDate().atStartOfDay();
            case "yesterday", "dün" -> LocalDateTime.now().minusDays(1).toLocalDate().atStartOfDay();
            case "week", "hafta" -> LocalDateTime.now().minusWeeks(1);
            case "month", "ay" -> LocalDateTime.now().minusMonths(1);
            case "quarter", "çeyrek" -> LocalDateTime.now().minusMonths(3);
            case "year", "yıl" -> LocalDateTime.now().minusYears(1);
            case "3days", "3gün" -> LocalDateTime.now().minusDays(3);
            case "7days", "7gün" -> LocalDateTime.now().minusDays(7);
            case "30days", "30gün" -> LocalDateTime.now().minusDays(30);
            case "90days", "90gün" -> LocalDateTime.now().minusDays(90);
            default -> LocalDateTime.now().minusMonths(1); // Varsayılan: 1 ay
        };
    }

    private Page<Notification> getFilteredAdminNotifications(AdminNotificationFilter filter, Pageable pageable) {
        // Custom query builder - mevcut repository metotlarını kullan
        if (filter.type() != null && !filter.type().isBlank()) {
            NotificationType type = NotificationType.valueOf(filter.type().toUpperCase());
            return notificationRepository.findByTypeOrderByCreatedAtDesc(type, pageable);
        }

        // Diğer filtreler için custom query gerekecek
        return notificationRepository.findAll(pageable);
    }

    private AdminNotificationResponse convertToAdminResponse(Notification notification) {
        // Aynı başlık/mesaja sahip bildirimleri groupla
        List<Notification> related = findRelatedNotifications(notification);

        int totalRecipients = related.size();
        int readCount = (int) related.stream().filter(Notification::isRead).count();
        int unreadCount = totalRecipients - readCount;
        double readPercentage = totalRecipients > 0 ? (double) readCount / totalRecipients * 100 : 0.0;

        String broadcastType = determineBroadcastType(related);
        String targetInfo = buildTargetInfo(related, broadcastType);

        LocalDateTime lastReadAt = related.stream()
                .filter(n -> n.getReadAt() != null)
                .map(Notification::getReadAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return new AdminNotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType(),
                notification.getType().getDisplayName(),
                notification.getPriority(),
                notification.getIcon(),
                notification.getCreatedAt(),
                totalRecipients,
                readCount,
                unreadCount,
                readPercentage,
                broadcastType,
                targetInfo,
                lastReadAt
        );
    }

    private List<Notification> findRelatedNotifications(Notification notification) {
        // Aynı başlık, mesaj ve oluşturulma zamanı (±5 dakika) ile bildirimleri bul
        LocalDateTime startTime = notification.getCreatedAt().minusMinutes(5);
        LocalDateTime endTime = notification.getCreatedAt().plusMinutes(5);

        // Repository metodunu kullan
        return notificationRepository.findBySimilarContentAndTimeRange(
                notification.getTitle(),
                notification.getMessage(),
                startTime,
                endTime
        );
    }

    private List<Notification> getNotificationsInRange(LocalDateTime startDate, LocalDateTime endDate) {
        // Repository metodunu kullan
        return notificationRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(startDate, endDate);
    }

    private Map<String, Integer> calculateReadStatsByHour(List<Notification> notifications) {
        if (notifications.isEmpty()) return Map.of();

        Notification first = notifications.get(0);
        LocalDateTime startTime = first.getCreatedAt().minusMinutes(5);
        LocalDateTime endTime = first.getCreatedAt().plusMinutes(5);

        // Repository metodunu kullan
        List<Object[]> results = notificationRepository.getHourlyReadDistribution(
                first.getTitle(),
                first.getMessage(),
                startTime,
                endTime
        );

        return results.stream()
                .collect(Collectors.toMap(
                        result -> String.valueOf(result[0]), // hour
                        result -> ((Number) result[1]).intValue() // count
                ));
    }

    private Map<String, Integer> calculateReadStatsByDay(List<Notification> notifications) {
        // Günlük okunma dağılımı
        return notifications.stream()
                .filter(n -> n.getReadAt() != null)
                .collect(Collectors.groupingBy(
                        n -> n.getReadAt().toLocalDate().toString(),
                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
                ));
    }



    private List<TopNotificationInfo> getTopUnreadNotifications(LocalDateTime startDate, LocalDateTime endDate) {
        List<NotificationStatus> notificationStatuses = Arrays.asList(NotificationStatus.READ, NotificationStatus.ARCHIVED);
        List<Object[]> results = notificationRepository.findTopUnreadNotifications(
                startDate, endDate,notificationStatuses, PageRequest.of(0, 5));

        return results.stream()
                .map(result -> new TopNotificationInfo(
                        null, // ID yok grup olduğu için
                        (String) result[0], // title
                        ((NotificationType) result[2]).name(), // type
                        ((Number) result[3]).intValue(), // totalRecipients
                        ((Number) result[4]).intValue(), // readCount
                        calculatePercentage(((Number) result[4]).intValue(), ((Number) result[3]).intValue()),
                        (LocalDateTime) result[5] // createdAt
                ))
                .toList();
    }

    private Map<String, Integer> getDailyNotificationTrend(LocalDateTime startDate, LocalDateTime endDate) {
        List<Object[]> results = notificationRepository.getDailyNotificationTrend(startDate, endDate);

        return results.stream()
                .collect(Collectors.toMap(
                        result -> result[0].toString(), // date
                        result -> ((Number) result[1]).intValue() // count
                ));
    }

    private Map<String, Double> getDailyReadRateTrend(LocalDateTime startDate, LocalDateTime endDate) {
        List<NotificationStatus> notificationStatuses = Arrays.asList(NotificationStatus.READ, NotificationStatus.ARCHIVED);
        List<Object[]> results = notificationRepository.getDailyReadRateTrend(startDate, endDate, notificationStatuses);

        return results.stream()
                .collect(Collectors.toMap(
                        result -> result[0].toString(), // date
                        result -> {
                            int total = ((Number) result[1]).intValue();
                            int read = ((Number) result[2]).intValue();
                            return calculatePercentage(read, total);
                        }
                ));
    }

    private double calculatePercentage(int part, int total) {
        return total > 0 ? (double) part / total * 100 : 0.0;
    }

// İhtiyaç halinde ek import'lar için:
// import java.time.Duration;
// import java.util.stream.Collectors;

    private AdminNotificationDetailResponse buildNotificationDetails(Notification notification, List<Notification> related) {
        int totalRecipients = related.size();
        int readCount = (int) related.stream().filter(Notification::isRead).count();
        int unreadCount = totalRecipients - readCount;

        // Zaman bazlı istatistikler
        Map<String, Integer> readStatsByHour = calculateReadStatsByHour(related);
        Map<String, Integer> readStatsByDay = calculateReadStatsByDay(related);

        // Örnek alıcılar (ilk 10)
        List<RecipientInfo> sampleRecipients = related.stream()
                .limit(10)
                .map(this::convertToRecipientInfo)
                .toList();

        // Timing bilgileri
        LocalDateTime firstReadAt = related.stream()
                .filter(n -> n.getReadAt() != null)
                .map(Notification::getReadAt)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        LocalDateTime lastReadAt = related.stream()
                .filter(n -> n.getReadAt() != null)
                .map(Notification::getReadAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        long averageReadTime = calculateAverageReadTime(related);

        return new AdminNotificationDetailResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getType().name(),
                notification.getPriority(),
                notification.getIcon(),
                notification.getActionUrl(),
                notification.getData(),
                notification.getCreatedAt(),
                totalRecipients,
                readCount,
                unreadCount,
                (double) readCount / totalRecipients * 100,
                readStatsByHour,
                readStatsByDay,
                determineBroadcastType(related),
                buildTargetInfo(related, determineBroadcastType(related)),
                sampleRecipients,
                firstReadAt,
                lastReadAt,
                averageReadTime
        );
    }

//    private AdminNotificationStatsResponse buildStatsResponse(List<Notification> notifications,
//                                                              LocalDateTime startDate,
//                                                              LocalDateTime endDate) {
//
//        int totalNotificationsSent = notifications.size();
//        int totalRecipients = notifications.size(); // Her notification bir alıcı
//
//        // Benzersiz kullanıcı sayısı
//        int totalUniqueUsers = (int) notifications.stream()
//                .map(n -> n.getUser().getId())
//                .distinct()
//                .count();
//
//        // Okunma istatistikleri
//        int totalReads = (int) notifications.stream().filter(Notification::isRead).count();
//        double overallReadRate = totalNotificationsSent > 0 ? (double) totalReads / totalNotificationsSent * 100 : 0.0;
//
//        // Tür bazlı istatistikler
//        Map<NotificationType, Integer> notificationsByType = notifications.stream()
//                .collect(Collectors.groupingBy(
//                        Notification::getType,
//                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
//                ));
//
//        Map<NotificationType, Double> readRatesByType = calculateReadRatesByType(notifications);
//
//        // Öncelik bazlı istatistikler
//        Map<String, Integer> notificationsByPriority = notifications.stream()
//                .collect(Collectors.groupingBy(
//                        Notification::getPriority,
//                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
//                ));
//
//        Map<String, Double> readRatesByPriority = calculateReadRatesByPriority(notifications);
//
//        return new AdminNotificationStatsResponse(
//                startDate,
//                endDate,
//                totalNotificationsSent,
//                totalRecipients,
//                totalUniqueUsers,
//                totalReads,
//                overallReadRate,
//                notificationsByType,
//                readRatesByType,
//                notificationsByPriority,
//                readRatesByPriority,
//                Map.of("ALL_USERS", totalNotificationsSent), // Basitleştirilmiş
//                List.of(), // Top notifications - ayrı hesaplama gerekli
//                List.of(), // Top unread notifications
//                Map.of(), // Daily trend - ayrı hesaplama gerekli
//                Map.of()  // Daily read rate trend
//        );
//    }

    private String determineBroadcastType(List<Notification> notifications) {
        if (notifications.isEmpty()) return "UNKNOWN";

        // Basit mantık - gerçek implementasyonda metadata'ya bakılabilir
        int userCount = (int) notifications.stream()
                .map(n -> n.getUser().getId())
                .distinct()
                .count();

        if (userCount > 100) return "ALL_USERS";
        if (userCount > 10) return "ROLE_BASED";
        if (userCount > 1) return "DEALER_SPECIFIC";
        return "SPECIFIC_USERS";
    }

    private String buildTargetInfo(List<Notification> notifications, String broadcastType) {
        int uniqueUsers = (int) notifications.stream()
                .map(n -> n.getUser().getId())
                .distinct()
                .count();

        return switch (broadcastType) {
            case "ALL_USERS" -> "Tüm Kullanıcılar (" + uniqueUsers + " kişi)";
            case "ROLE_BASED" -> "Belirli Rol (" + uniqueUsers + " kişi)";
            case "DEALER_SPECIFIC" -> "Bayi Kullanıcıları (" + uniqueUsers + " kişi)";
            case "SPECIFIC_USERS" -> "Seçili Kullanıcılar (" + uniqueUsers + " kişi)";
            default -> uniqueUsers + " kullanıcı";
        };
    }

    private RecipientInfo convertToRecipientInfo(Notification notification) {
        AppUser user = notification.getUser();
        return new RecipientInfo(
                user.getId(),
                user.getFirstName() + " " + user.getLastName(),
                user.getEmail(),
                notification.isRead(),
                notification.getReadAt(),
                user.getDealer() != null ? user.getDealer().getName() : "Bağımsız"
        );
    }

//    private Map<String, Integer> calculateReadStatsByHour(List<Notification> notifications) {
//        // 24 saatlik dilimde okunma dağılımı
//        return notifications.stream()
//                .filter(n -> n.getReadAt() != null)
//                .collect(Collectors.groupingBy(
//                        n -> String.valueOf(n.getReadAt().getHour()),
//                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
//                ));
//    }

//    private Map<String, Integer> calculateReadStatsByDay(List<Notification> notifications) {
//        // Günlük okunma dağılımı
//        return notifications.stream()
//                .filter(n -> n.getReadAt() != null)
//                .collect(Collectors.groupingBy(
//                        n -> n.getReadAt().toLocalDate().toString(),
//                        Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
//                ));
//    }

    private long calculateAverageReadTime(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return 0L;
        }

        List<Long> readTimes = notifications.stream()
                .filter(n -> n.getReadAt() != null && n.getCreatedAt() != null)
                .map(n -> java.time.Duration.between(n.getCreatedAt(), n.getReadAt()).toMinutes())
                .filter(minutes -> minutes >= 0) // Negatif değerleri filtrele
                .collect(Collectors.toList());

        if (readTimes.isEmpty()) {
            return 0L;
        }

        double average = readTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);

        return Math.round(average);
    }

    private Map<NotificationType, Double> calculateReadRatesByType(List<Notification> notifications) {
        Map<NotificationType, List<Notification>> byType = notifications.stream()
                .collect(Collectors.groupingBy(Notification::getType));

        return byType.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            List<Notification> typeNotifications = entry.getValue();
                            long readCount = typeNotifications.stream().filter(Notification::isRead).count();
                            return typeNotifications.isEmpty() ? 0.0 : (double) readCount / typeNotifications.size() * 100;
                        }
                ));
    }
    private Map<String, Double> calculateReadRatesByPriority(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return Map.of();
        }

        // Bildirimleri öncelik seviyesine göre grupla
        Map<String, List<Notification>> notificationsByPriority = notifications.stream()
                .filter(n -> n.getPriority() != null) // Null priority kontrolü
                .collect(Collectors.groupingBy(Notification::getPriority));

        // Her öncelik seviyesi için okunma oranını hesapla
        return notificationsByPriority.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, // Öncelik seviyesi (LOW, MEDIUM, HIGH, URGENT)
                        entry -> {
                            List<Notification> priorityNotifications = entry.getValue();

                            if (priorityNotifications.isEmpty()) {
                                return 0.0;
                            }

                            // Okunan bildirim sayısını hesapla
                            long readCount = priorityNotifications.stream()
                                    .filter(Notification::isRead) // isRead() metodu READ veya ARCHIVED durumunu kontrol eder
                                    .count();

                            // Okunma yüzdesini hesapla
                            double readPercentage = (double) readCount / priorityNotifications.size() * 100.0;

                            // 2 ondalık basamakla yuvarla
                            return Math.round(readPercentage * 100.0) / 100.0;
                        }
                ));
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