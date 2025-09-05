package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Aktivite verisi")
public record ActivityData(
        @Schema(description = "Aktivite ID", example = "1")
        Long id,

        @Schema(description = "Aktivite tipi", example = "ORDER_CREATED")
        String activityType,

        @Schema(description = "Başlık", example = "Yeni Sipariş")
        String title,

        @Schema(description = "Açıklama", example = "ABC Medikal Ltd. yeni sipariş oluşturdu")
        String description,

        @Schema(description = "Kullanıcı adı", example = "Ahmet Yılmaz")
        String userName,

        @Schema(description = "İlgili entity ID", example = "ORD-20240315-001")
        String relatedEntityId,

        @Schema(description = "İlgili entity tipi", example = "ORDER")
        String relatedEntityType,

        @Schema(description = "Aksiyon URL'i", example = "/admin/orders/123")
        String actionUrl,

        @Schema(description = "Oluşturulma zamanı")
        LocalDateTime createdAt,

        @Schema(description = "İkon", example = "shopping-cart")
        String icon,

        @Schema(description = "Renk", example = "success")
        String color
) {}