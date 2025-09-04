package com.maxx_global.repository;

import com.maxx_global.entity.Notification;
import com.maxx_global.enums.NotificationStatus;
import com.maxx_global.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Kullanıcının bildirimlerini getir (sayfalama ile)
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Kullanıcının belirli durumdaki bildirimlerini getir
    Page<Notification> findByUserIdAndNotificationStatusOrderByCreatedAtDesc(
            Long userId, NotificationStatus notificationStatus, Pageable pageable);

    // Okunmamış bildirim sayısı
    long countByUserIdAndNotificationStatus(Long userId, NotificationStatus notificationStatus);

    // Belirli türdeki bildirimleri getir
    Page<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(
            Long userId, NotificationType type, Pageable pageable);

    // Kullanıcının son N günlük bildirimlerini getir
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId " +
            "AND n.createdAt >= :startDate ORDER BY n.createdAt DESC")
    List<Notification> findRecentNotifications(@Param("userId") Long userId,
                                               @Param("startDate") LocalDateTime startDate);

    // Kullanıcının tüm bildirimlerini okunmuş olarak işaretle
    @Modifying
    @Query("UPDATE Notification n SET n.notificationStatus = :status, n.readAt = :readAt " +
            "WHERE n.user.id = :userId AND n.notificationStatus = :unreadStatus")
    int markAllAsReadByUserId(@Param("userId") Long userId,
                              @Param("status") NotificationStatus status,
                              @Param("readAt") LocalDateTime readAt,
                              @Param("unreadStatus") NotificationStatus unreadStatus);

    // Eski bildirimleri temizle (retention policy için)
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoffDate")
    int deleteOldNotifications(@Param("cutoffDate") LocalDateTime cutoffDate);

    // Belirli entity ile ilgili bildirimleri bul
    List<Notification> findByRelatedEntityIdAndRelatedEntityType(Long entityId, String entityType);

    // Kullanıcının en son bildirimlerini getir (limit ile)
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId " +
            "ORDER BY n.createdAt DESC LIMIT :limit")
    List<Notification> findTopNotificationsByUserId(@Param("userId") Long userId,
                                                    @Param("limit") int limit);

    // Öncelikli bildirimleri getir
    Page<Notification> findByUserIdAndPriorityOrderByCreatedAtDesc(
            Long userId, String priority, Pageable pageable);

    // Birden fazla bildirimi toplu güncelle
    @Modifying
    @Query("UPDATE Notification n SET n.notificationStatus = :status, n.readAt = :readAt " +
            "WHERE n.id IN :notificationIds AND n.user.id = :userId")
    int bulkUpdateStatus(@Param("notificationIds") List<Long> notificationIds,
                         @Param("userId") Long userId,
                         @Param("status") NotificationStatus status,
                         @Param("readAt") LocalDateTime readAt);

    // İstatistik metodları
    long countByCreatedAtAfter(LocalDateTime startDate);

    long countByCreatedAtAfterAndNotificationStatus(LocalDateTime startDate, NotificationStatus status);

    @Query("SELECT n.type, COUNT(n) FROM Notification n WHERE n.createdAt >= :startDate GROUP BY n.type")
    Map<NotificationType, Long> countNotificationsByTypeAfter(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT n.priority, COUNT(n) FROM Notification n WHERE n.createdAt >= :startDate GROUP BY n.priority")
    Map<String, Long> countNotificationsByPriorityAfter(@Param("startDate") LocalDateTime startDate);

    // NotificationRepository.java içine eklenecek metodlar:

// ==================== CLEANUP QUERY METODLARI ====================

    /**
     * Okunmuş bildirimleri belirli tarihten önce sil
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.notificationStatus IN :statuses " +
            "AND n.readAt IS NOT NULL AND n.readAt < :cutoffDate")
    int deleteReadNotificationsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate,
                                         @Param("statuses") List<NotificationStatus> statuses);

    /**
     * Belirli kullanıcının okunmuş bildirimlerini sil
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId " +
            "AND n.notificationStatus IN :statuses " +
            "AND n.readAt IS NOT NULL AND n.readAt < :cutoffDate")
    int deleteUserReadNotificationsOlderThan(@Param("userId") Long userId,
                                             @Param("cutoffDate") LocalDateTime cutoffDate,
                                             @Param("statuses") List<NotificationStatus> statuses);

    /**
     * Tüm eski bildirimleri sil (emergency cleanup için)
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoffDate")
    int deleteAllNotificationsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Cleanup için uygun okunmuş notification sayısını say
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.notificationStatus IN :statuses " +
            "AND n.readAt IS NOT NULL AND n.readAt < :cutoffDate")
    int countReadNotificationsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate,
                                        @Param("statuses") List<NotificationStatus> statuses);

    /**
     * Arşivlenmiş bildirimleri say
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.notificationStatus = :status " +
            "AND n.readAt IS NOT NULL AND n.readAt < :cutoffDate")
    int countArchivedNotificationsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate,
                                            @Param("status") NotificationStatus status);

    /**
     * Belirli statuslardaki notification sayısını say
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.notificationStatus IN :statuses")
    int countByNotificationStatusIn(@Param("statuses") List<NotificationStatus> statuses);

    /**
     * Kullanıcı bazında cleanup breakdown'u
     */
    @Query("SELECT CONCAT(u.firstName, ' ', u.lastName) as userName, COUNT(n) as count " +
            "FROM Notification n JOIN n.user u " +
            "WHERE n.notificationStatus IN :statuses " +
            "AND n.readAt IS NOT NULL AND n.readAt < :cutoffDate " +
            "GROUP BY u.id, u.firstName, u.lastName " +
            "ORDER BY count DESC")
    List<Object[]> getCleanupBreakdownByUserRaw(@Param("cutoffDate") LocalDateTime cutoffDate,
                                                @Param("statuses") List<NotificationStatus> statuses);

    /**
     * Cleanup breakdown'unu Map olarak döndür
     */
    default Map<String, Integer> getCleanupBreakdownByUser(LocalDateTime cutoffDate) {
        List<NotificationStatus> notificationStatuses = new ArrayList<>();
        notificationStatuses.add(NotificationStatus.READ);
        notificationStatuses.add(NotificationStatus.ARCHIVED);
        List<Object[]> results = getCleanupBreakdownByUserRaw(cutoffDate,notificationStatuses);
        return results.stream()
                .collect(Collectors.toMap(
                        result -> (String) result[0],  // userName
                        result -> ((Number) result[1]).intValue()  // count
                ));
    }

    /**
     * Belirli tip bildirimleri temizle
     */
//    @Modifying
//    @Query("DELETE FROM Notification n WHERE n.type = :notificationType " +
//            "AND n.notificationStatus IN ('READ', 'ARCHIVED') " +
//            "AND n.readAt < :cutoffDate")
//    int deleteReadNotificationsByType(@Param("notificationType") NotificationType notificationType,
//                                      @Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Toplu cleanup - batch'ler halinde sil (performans için)
     */
//    @Modifying
//    @Query(value = "DELETE FROM notifications WHERE notification_status IN ('READ', 'ARCHIVED') " +
//            "AND read_at IS NOT NULL AND read_at < :cutoffDate LIMIT :batchSize",
//            nativeQuery = true)
//    int deleteBatchOfReadNotifications(@Param("cutoffDate") LocalDateTime cutoffDate,
//                                       @Param("batchSize") int batchSize);

    /**
     * Cleanup için en eski okunmuş bildirimi bul
     */
//    @Query("SELECT MIN(n.readAt) FROM Notification n WHERE n.notificationStatus IN ('READ', 'ARCHIVED') " +
//            "AND n.readAt IS NOT NULL")
//    LocalDateTime findOldestReadNotificationDate();

    /**
     * Cleanup için en yeni okunmuş bildirimi bul
     */
//    @Query("SELECT MAX(n.readAt) FROM Notification n WHERE n.notificationStatus IN ('READ', 'ARCHIVED') " +
//            "AND n.readAt IS NOT NULL")
//    LocalDateTime findNewestReadNotificationDate();

    /**
     * Kullanıcı başına ortalama notification sayısı
     */
    @Query("SELECT AVG(userCounts.notificationCount) FROM " +
            "(SELECT COUNT(n) as notificationCount FROM Notification n GROUP BY n.user.id) as userCounts")
    Double getAverageNotificationsPerUser();

    /**
     * Cleanup istatistikleri için günlük breakdown
     */
//    @Query("SELECT DATE(n.readAt) as readDate, COUNT(n) as count " +
//            "FROM Notification n " +
//            "WHERE n.notificationStatus IN ('READ', 'ARCHIVED') " +
//            "AND n.readAt IS NOT NULL " +
//            "AND n.readAt >= :startDate AND n.readAt <= :endDate " +
//            "GROUP BY DATE(n.readAt) " +
//            "ORDER BY readDate DESC")
//    List<Object[]> getReadNotificationStatsByDateRange(@Param("startDate") LocalDateTime startDate,
//                                                       @Param("endDate") LocalDateTime endDate);

    /**
     * Cleanup öncesi güvenlik kontrolü - kritik bildirimleri say
     */
//    @Query("SELECT COUNT(n) FROM Notification n WHERE n.priority = 'URGENT' " +
//            "AND n.notificationStatus = 'READ' AND n.readAt < :cutoffDate")
//    int countCriticalNotificationsForCleanup(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Son cleanup işleminin etkisini ölç
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.createdAt > :lastCleanupTime")
    int countNotificationsCreatedSince(@Param("lastCleanupTime") LocalDateTime lastCleanupTime);

    /**
     * En çok notification'a sahip kullanıcıları bul (cleanup planlaması için)
//     */
//    @Query("SELECT u.id, CONCAT(u.firstName, ' ', u.lastName) as fullName, COUNT(n) as notificationCount " +
//            "FROM Notification n JOIN n.user u " +
//            "WHERE n.notificationStatus IN ('read', 'ARCHIVED') " +
//            "GROUP BY u.id, u.firstName, u.lastName " +
//            "HAVING COUNT(n) > :threshold " +
//            "ORDER BY notificationCount DESC")
//    List<Object[]> findUsersWithExcessiveNotifications(@Param("threshold") int threshold);
//
//    /**
//     * Cleanup performansı için index hint
//     */
//    @Query(value = "SELECT COUNT(*) FROM notifications USE INDEX (idx_notification_status_read_at) " +
//            "WHERE notification_status IN ('read', 'archived') " +
//            "AND read_at IS NOT NULL AND read_at < :cutoffDate",
//            nativeQuery = true)
//    int countReadNotificationsOptimized(@Param("cutoffDate") LocalDateTime cutoffDate);
}
