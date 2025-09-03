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
import java.util.List;
import java.util.Map;

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
}
