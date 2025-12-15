package com.maxx_global.entity;

import com.maxx_global.enums.NotificationStatus;
import com.maxx_global.enums.NotificationType;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_created_at", columnList = "created_at"),
        @Index(name = "idx_type", columnList = "type")
})
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title_tr", length = 200)
    private String title;

    @Column(name = "title_en", length = 200)
    private String titleEn;


    @Column(name = "message_tr", length = 1000)
    private String message;

    @Column(name = "message_en", length = 1000)
    private String messageEn;

    // Bildirim tipi
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private NotificationType type;

    // İlgili entity ID'si (order_id, product_id vb.)
    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    // İlgili entity tipi (frontend routing için)
    @Column(name = "related_entity_type", length = 50)
    private String relatedEntityType;

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

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTitleEn() {
        return titleEn;
    }

    public void setTitleEn(String titleEn) {
        this.titleEn = titleEn;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getMessageEn() {
        return messageEn;
    }

    public void setMessageEn(String messageEn) {
        this.messageEn = messageEn;
    }

    public NotificationType getType() { return type; }
    public void setType(NotificationType type) { this.type = type; }

    public Long getRelatedEntityId() { return relatedEntityId; }
    public void setRelatedEntityId(Long relatedEntityId) { this.relatedEntityId = relatedEntityId; }

    public String getRelatedEntityType() { return relatedEntityType; }
    public void setRelatedEntityType(String relatedEntityType) { this.relatedEntityType = relatedEntityType; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getActionUrl() { return actionUrl; }
    public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

}