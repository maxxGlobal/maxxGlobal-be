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

    // NotificationRepository.java'ya eklenecek metotlar:

    /**
     * Tarih aralığında oluşturulan bildirimleri getir
     */
    @Query("SELECT n FROM Notification n WHERE n.createdAt BETWEEN :startDate AND :endDate ORDER BY n.createdAt DESC")
    List<Notification> findByCreatedAtBetweenOrderByCreatedAtDesc(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Benzer içerik ve zaman aralığındaki bildirimleri bul (aynı broadcast'e ait)
     */
    @Query("SELECT n FROM Notification n WHERE n.title = :title AND n.message = :message " +
            "AND n.createdAt BETWEEN :startTime AND :endTime ORDER BY n.createdAt DESC")
    List<Notification> findBySimilarContentAndTimeRange(
            @Param("title") String title,
            @Param("message") String message,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * Admin filtreli bildirim arama
//     */
//    @Query("SELECT n FROM Notification n WHERE " +
//            "(:type IS NULL OR n.type = :type) AND " +
//            "(:priority IS NULL OR n.priority = :priority) AND " +
//            "(:startDate IS NULL OR n.createdAt >= :startDate) AND " +
//            "(:endDate IS NULL OR n.createdAt <= :endDate) AND " +
//            "(:searchTerm IS NULL OR " +
//            "LOWER(n.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
//            "LOWER(n.message) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
//            "ORDER BY n.createdAt DESC")
//    Page<Notification> findAdminNotificationsWithFilter(
//            @Param("type") NotificationType type,
//            @Param("priority") String priority,
//            @Param("startDate") LocalDateTime startDate,
//            @Param("endDate") LocalDateTime endDate,
//            @Param("searchTerm") String searchTerm,
//            Pageable pageable);

    /**
     * Broadcast gruplarını benzersiz olarak getir (aynı başlık/mesaj/zaman)
     */
//    @Query("SELECT n FROM Notification n WHERE n.id IN (" +
//            "SELECT MIN(n2.id) FROM Notification n2 " +
//            "GROUP BY n2.title, n2.message, DATE(n2.createdAt), HOUR(n2.createdAt)" +
//            ") ORDER BY n.createdAt DESC")
//    Page<Notification> findUniqueNotificationGroups(Pageable pageable);

    /**
     * Belirli zaman aralığındaki benzersiz notification gruplarını getir
     */
//    @Query("SELECT n FROM Notification n WHERE n.id IN (" +
//            "SELECT MIN(n2.id) FROM Notification n2 " +
//            "WHERE n2.createdAt BETWEEN :startDate AND :endDate " +
//            "GROUP BY n2.title, n2.message, DATE(n2.createdAt), HOUR(n2.createdAt)" +
//            ") ORDER BY n.createdAt DESC")
//    List<Notification> findUniqueNotificationGroupsInRange(
//            @Param("startDate") LocalDateTime startDate,
//            @Param("endDate") LocalDateTime endDate);

    /**
     * En çok okunan bildirimleri getir
     */
    @Query(value = "SELECT n.title, n.message, n.type, " +
            "COUNT(*) as total_recipients, " +
            "SUM(CASE WHEN n.notification_status IN ('READ', 'ARCHIVED') THEN 1 ELSE 0 END) as read_count, " +
            "MIN(n.created_at) as created_at " +
            "FROM notifications n " +
            "WHERE n.created_at BETWEEN ?1 AND ?2 " +
            "GROUP BY n.title, n.message, n.type " +
            "HAVING COUNT(*) > 1 " +
            "ORDER BY (SUM(CASE WHEN n.notification_status IN ('read', 'ARCHIVED') THEN 1 ELSE 0 END)::float / COUNT(*)) DESC " +
            "LIMIT 5",
            nativeQuery = true)
    List<Object[]> findTopReadNotifications(
            LocalDateTime startDate,
            LocalDateTime endDate);

    /**
     * En az okunan bildirimleri getir
     */
    @Query("SELECT n.title, n.message, n.type, " +
            "COUNT(n) as totalRecipients, " +
            "SUM(CASE WHEN n.notificationStatus IN :status THEN 1 ELSE 0 END) as readCount, " +
            "MIN(n.createdAt) as createdAt " +
            "FROM Notification n " +
            "WHERE n.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY n.title, n.message, n.type " +
            "HAVING COUNT(n) > 1 " +
            "ORDER BY (SUM(CASE WHEN n.notificationStatus IN :status THEN 1 ELSE 0 END) / COUNT(n)) ASC")
    List<Object[]> findTopUnreadNotifications(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("status") List<NotificationStatus> status,
            Pageable pageable);

    /**
     * Günlük bildirim trend verisi
     */
    @Query("SELECT DATE(n.createdAt) as notificationDate, COUNT(n) as count " +
            "FROM Notification n " +
            "WHERE n.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY DATE(n.createdAt) " +
            "ORDER BY n.createdAt DESC")
    List<Object[]> getDailyNotificationTrend(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);



    /**
     * Alternatif olarak @Query annotation ile:
     */
    @Query("SELECT n FROM Notification n WHERE n.type = :type ORDER BY n.createdAt DESC")
    Page<Notification> findByTypeOrderByCreatedAtDesc(@Param("type") NotificationType type, Pageable pageable);

    /**
     * Günlük okunma oranı trend verisi
     */
    @Query("SELECT DATE(n.createdAt) as notificationDate, " +
            "COUNT(n) as totalCount, " +
            "SUM(CASE WHEN n.notificationStatus IN :status THEN 1 ELSE 0 END) as readCount " +
            "FROM Notification n " +
            "WHERE n.createdAt BETWEEN :startDate AND :endDate " +
            "GROUP BY DATE(n.createdAt) " +
            "ORDER BY n.createdAt DESC")
    List<Object[]> getDailyReadRateTrend(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("status") List<NotificationStatus> status);

    /**
     * Saatlik okunma dağılımı
     */
    @Query("SELECT HOUR(n.readAt) as readHour, COUNT(n) as count " +
            "FROM Notification n " +
            "WHERE n.readAt IS NOT NULL " +
            "AND n.title = :title AND n.message = :message " +
            "AND n.createdAt BETWEEN :startTime AND :endTime " +
            "GROUP BY HOUR(n.readAt) " +
            "ORDER BY HOUR(n.readAt)")
    List<Object[]> getHourlyReadDistribution(
            @Param("title") String title,
            @Param("message") String message,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);


    /**
     * Belirli bildirimin alıcı detaylarını getir
     */
//    @Query("SELECT n, u, d FROM Notification n " +
//            "JOIN n.user u " +
//            "LEFT JOIN u.dealer d " +
//            "WHERE n.title = :title AND n.message = :message " +
//            "AND n.createdAt BETWEEN :startTime AND :endTime " +
//            "ORDER BY n.readAt DESC NULLS LAST")
//    List<Object[]> getNotificationRecipientDetails(
//            @Param("title") String title,
//            @Param("message") String message,
//            @Param("startTime") LocalDateTime startTime,
//            @Param("endTime") LocalDateTime endTime);

    /**
     * Kullanıcı bazında admin bildirimleri istatistikleri
     */
//    @Query("SELECT u.id, CONCAT(u.firstName, ' ', u.lastName) as fullName, " +
//            "COUNT(n) as totalReceived, " +
//            "SUM(CASE WHEN n.notificationStatus IN ('read', 'ARCHIVED') THEN 1 ELSE 0 END) as totalRead, " +
//            "AVG(CASE WHEN n.readAt IS NOT NULL " +
//            "THEN TIMESTAMPDIFF(MINUTE, n.createdAt, n.readAt) ELSE NULL END) as avgReadTimeMinutes " +
//            "FROM Notification n " +
//            "JOIN n.user u " +
//            "WHERE n.createdAt BETWEEN :startDate AND :endDate " +
//            "GROUP BY u.id, u.firstName, u.lastName " +
//            "ORDER BY totalReceived DESC")
//    List<Object[]> getUserNotificationStats(
//            @Param("startDate") LocalDateTime startDate,
//            @Param("endDate") LocalDateTime endDate);

    /**
     * Bayi bazında bildirim istatistikleri
     */
//    @Query("SELECT d.name as dealerName, " +
//            "COUNT(n) as totalNotifications, " +
//            "SUM(CASE WHEN n.notificationStatus IN ('read', 'ARCHIVED') THEN 1 ELSE 0 END) as readCount, " +
//            "COUNT(DISTINCT u.id) as uniqueUsers " +
//            "FROM Notification n " +
//            "JOIN n.user u " +
//            "LEFT JOIN u.dealer d " +
//            "WHERE n.createdAt BETWEEN :startDate AND :endDate " +
//            "GROUP BY d.id, d.name " +
//            "ORDER BY totalNotifications DESC")
//    List<Object[]> getDealerNotificationStats(
//            @Param("startDate") LocalDateTime startDate,
//            @Param("endDate") LocalDateTime endDate);

    /**
     * Bildirim performans analizi
     */
//    @Query("SELECT n.type, n.priority, " +
//            "COUNT(n) as totalSent, " +
//            "SUM(CASE WHEN n.notificationStatus IN ('read', 'ARCHIVED') THEN 1 ELSE 0 END) as totalRead, " +
//            "AVG(CASE WHEN n.readAt IS NOT NULL " +
//            "THEN TIMESTAMPDIFF(MINUTE, n.createdAt, n.readAt) ELSE NULL END) as avgReadTimeMinutes, " +
//            "MIN(n.createdAt) as firstSent, " +
//            "MAX(n.createdAt) as lastSent " +
//            "FROM Notification n " +
//            "WHERE n.createdAt BETWEEN :startDate AND :endDate " +
//            "GROUP BY n.type, n.priority " +
//            "ORDER BY totalSent DESC")
//    List<Object[]> getNotificationPerformanceAnalysis(
//            @Param("startDate") LocalDateTime startDate,
//            @Param("endDate") LocalDateTime endDate);

}
