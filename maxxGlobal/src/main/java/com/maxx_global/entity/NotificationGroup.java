// NotificationGroup.java (Entity sınıfı)
package com.maxx_global.entity;

import com.maxx_global.enums.NotificationType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification_groups")
public class NotificationGroup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Column(name = "priority", nullable = false)
    private String priority;

    @Column(name = "icon")
    private String icon;

    @Column(name = "action_url")
    private String actionUrl;

    @Column(name = "data", columnDefinition = "TEXT")
    private String data;

    @Column(name = "broadcast_type", nullable = false)
    private String broadcastType; // ALL_USERS, ROLE_BASED, DEALER_SPECIFIC, SPECIFIC_USERS

    @Column(name = "target_info")
    private String targetInfo; // Hedef kitle açıklaması

    @Column(name = "total_recipients", nullable = false)
    private Integer totalRecipients = 0;

    @Column(name = "read_count", nullable = false)
    private Integer readCount = 0;

    @Column(name = "unread_count", nullable = false)
    private Integer unreadCount = 0;

    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getActionUrl() { return actionUrl; }
    public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public String getBroadcastType() { return broadcastType; }
    public void setBroadcastType(String broadcastType) { this.broadcastType = broadcastType; }

    public String getTargetInfo() { return targetInfo; }
    public void setTargetInfo(String targetInfo) { this.targetInfo = targetInfo; }

    public Integer getTotalRecipients() { return totalRecipients; }
    public void setTotalRecipients(Integer totalRecipients) { this.totalRecipients = totalRecipients; }

    public Integer getReadCount() { return readCount; }
    public void setReadCount(Integer readCount) { this.readCount = readCount; }

    public Integer getUnreadCount() { return unreadCount; }
    public void setUnreadCount(Integer unreadCount) { this.unreadCount = unreadCount; }

    public LocalDateTime getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(LocalDateTime lastReadAt) { this.lastReadAt = lastReadAt; }

    // Helper methods
    public double getReadPercentage() {
        return totalRecipients > 0 ? (double) readCount / totalRecipients * 100 : 0.0;
    }

    public void updateStats(int newReadCount, int newUnreadCount) {
        this.readCount = newReadCount;
        this.unreadCount = newUnreadCount;
        this.totalRecipients = newReadCount + newUnreadCount;
    }
}