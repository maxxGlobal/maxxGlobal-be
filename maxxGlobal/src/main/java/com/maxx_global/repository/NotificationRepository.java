package com.maxx_global.repository;

import com.maxx_global.entity.Notification;
import com.maxx_global.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByCreatedAtAfter(LocalDateTime startDate);

    long countByCreatedAtAfterAndType(LocalDateTime startDate, NotificationType type);

    @Query("SELECT n.type, COUNT(n) FROM Notification n WHERE n.createdAt >= :startDate GROUP BY n.type")
    Map<NotificationType, Long> countNotificationsByTypeAfter(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT n.priority, COUNT(n) FROM Notification n WHERE n.createdAt >= :startDate GROUP BY n.priority")
    Map<String, Long> countNotificationsByPriorityAfter(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT n FROM Notification n WHERE n.createdAt BETWEEN :startDate AND :endDate ORDER BY n.createdAt DESC")
    List<Notification> findByCreatedAtBetweenOrderByCreatedAtDesc(@Param("startDate") LocalDateTime startDate,
                                                                   @Param("endDate") LocalDateTime endDate);

    @Query("SELECT n.id FROM Notification n WHERE n.id NOT IN (SELECT nr.notification.id FROM NotificationRecipient nr)")
    List<Long> findOrphanedNotificationIds();

    void deleteByIdIn(List<Long> ids);
}
