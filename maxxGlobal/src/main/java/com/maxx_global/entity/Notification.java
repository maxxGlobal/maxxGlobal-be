package com.maxx_global.entity;

import com.maxx_global.enums.NotificationStatus;
import com.maxx_global.enums.NotificationType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_user_status", columnList = "user_id, status"),
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_type", columnList = "type")
})
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Bildirimi alan kullanıcı
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    // Bildirim başlığı
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    // Bildirim mesajı
    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    // Bildirim tipi
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    // Bildirim durumu
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_status", nullable = false, length = 20)
    private NotificationStatus notificationStatus = NotificationStatus.UNREAD;

    // İlgili entity ID'si (order_id, product_id vb.)
    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    // İlgili entity tipi (frontend routing için)
    @Column(name = "related_entity_type", length = 50)
    private String relatedEntityType;

    // Okunma tarihi
    @Column(name = "read_at")
    private LocalDateTime readAt;

    // Öncelik seviyesi
    @Column(name = "priority", length = 20, nullable = false)
    private String priority = "MEDIUM"; // LOW, MEDIUM, HIGH, URGENT

    // Frontend'de gösterilecek ikon
    @Column(name = "icon", length = 50)
    private String icon;

    // Frontend'de yönlendirme URL'i
    @Column(name = "action_url", length = 500)
    private String actionUrl;

    // Bildirim verisi (JSON format - ek bilgiler için)
    @Column(name = "data", columnDefinition = "TEXT")
    private String data;

    // Getter ve Setter'lar
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public NotificationStatus getNotificationStatus() {
        return notificationStatus;
    }

    public void setNotificationStatus(NotificationStatus notificationStatus) {
        this.notificationStatus = notificationStatus;
    }

    public Long getRelatedEntityId() { return relatedEntityId; }
    public void setRelatedEntityId(Long relatedEntityId) { this.relatedEntityId = relatedEntityId; }

    public String getRelatedEntityType() { return relatedEntityType; }
    public void setRelatedEntityType(String relatedEntityType) { this.relatedEntityType = relatedEntityType; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getActionUrl() { return actionUrl; }
    public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    // Helper methodlar
    public boolean isRead() {
        return notificationStatus == NotificationStatus.READ || notificationStatus == NotificationStatus.ARCHIVED;
    }

    public void markAsRead() {
        this.notificationStatus = NotificationStatus.READ;
        this.readAt = LocalDateTime.now();
    }

    public void markAsUnread() {
        this.notificationStatus = NotificationStatus.UNREAD;
        this.readAt = null;
    }

    public void archive() {
        this.notificationStatus = NotificationStatus.ARCHIVED;
        if (this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }
}