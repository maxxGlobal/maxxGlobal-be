package com.maxx_global.repository;

import com.maxx_global.entity.NotificationRecipient;
import com.maxx_global.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    Page<NotificationRecipient> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<NotificationRecipient> findByUserIdAndNotificationStatusOrderByCreatedAtDesc(Long userId, NotificationStatus status, Pageable pageable);

    Page<NotificationRecipient> findByUserIdAndNotificationNotificationTypeOrderByCreatedAtDesc(Long userId, com.maxx_global.enums.NotificationType type, Pageable pageable);

    Page<NotificationRecipient> findByUserIdAndNotificationStatusAndNotificationNotificationTypeOrderByCreatedAtDesc(Long userId, NotificationStatus status, com.maxx_global.enums.NotificationType type, Pageable pageable);

    Page<NotificationRecipient> findByUserIdAndNotificationPriorityOrderByCreatedAtDesc(Long userId, String priority, Pageable pageable);

    @Query("SELECT COUNT(nr) FROM NotificationRecipient nr WHERE nr.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    long countByUserIdAndNotificationStatus(Long userId, NotificationStatus status);

    @Query("SELECT COUNT(nr) FROM NotificationRecipient nr WHERE nr.user.id = :userId AND nr.createdAt >= :start")
    long countByUserIdAndCreatedAtAfter(@Param("userId") Long userId, @Param("start") LocalDateTime start);

    @Query("SELECT COUNT(nr) FROM NotificationRecipient nr WHERE nr.user.id = :userId AND nr.notificationStatus = :status AND nr.notification.priority = :priority")
    long countByUserIdAndNotificationStatusAndNotificationPriority(@Param("userId") Long userId,
                                                                   @Param("status") NotificationStatus status,
                                                                   @Param("priority") String priority);

    java.util.Optional<NotificationRecipient> findByNotificationIdAndUserId(Long notificationId, Long userId);

    @Modifying
    @Query("UPDATE NotificationRecipient nr SET nr.notificationStatus = :status, nr.readAt = :readAt WHERE nr.user.id = :userId AND nr.notificationStatus = :unreadStatus")
    int markAllAsRead(@Param("userId") Long userId,
                      @Param("status") NotificationStatus status,
                      @Param("readAt") LocalDateTime readAt,
                      @Param("unreadStatus") NotificationStatus unreadStatus);

    @Modifying
    @Query("UPDATE NotificationRecipient nr SET nr.notificationStatus = :status, nr.readAt = :readAt WHERE nr.id IN :ids AND nr.user.id = :userId")
    int bulkUpdateStatus(@Param("ids") List<Long> ids,
                         @Param("userId") Long userId,
                         @Param("status") NotificationStatus status,
                         @Param("readAt") LocalDateTime readAt);

    @Query("SELECT COUNT(nr) FROM NotificationRecipient nr WHERE nr.createdAt BETWEEN :startDate AND :endDate")
    long countByCreatedAtBetween(@Param("startDate") LocalDateTime startDate,
                                 @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(nr) FROM NotificationRecipient nr WHERE nr.createdAt BETWEEN :startDate AND :endDate AND nr.notificationStatus IN :statuses")
    long countByStatusBetween(@Param("startDate") LocalDateTime startDate,
                              @Param("endDate") LocalDateTime endDate,
                              @Param("statuses") List<NotificationStatus> statuses);

    @Query("SELECT COUNT(DISTINCT nr.user.id) FROM NotificationRecipient nr WHERE nr.createdAt BETWEEN :startDate AND :endDate")
    long countDistinctUsersBetween(@Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate);

    List<NotificationRecipient> findByNotificationIdIn(List<Long> notificationIds);

    List<NotificationRecipient> findByNotificationId(Long notificationId);
}
