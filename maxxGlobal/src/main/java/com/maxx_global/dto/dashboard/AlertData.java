package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Uyarı verisi")
public record AlertData(
        @Schema(description = "Uyarı ID", example = "1")
        Long id,

        @Schema(description = "Uyarı tipi", example = "LOW_STOCK")
        String alertType,

        @Schema(description = "Başlık", example = "Düşük Stok Uyarısı")
        String title,

        @Schema(description = "Mesaj", example = "5 ürünün stoğu kritik seviyede")
        String message,

        @Schema(description = "Önem seviyesi", example = "HIGH")
        String severity,

        @Schema(description = "İlgili entity ID", example = "123")
        Long relatedEntityId,

        @Schema(description = "İlgili entity tipi", example = "PRODUCT")
        String relatedEntityType,

        @Schema(description = "Aksiyon URL'i", example = "/admin/products")
        String actionUrl,

        @Schema(description = "Oluşturulma zamanı")
        LocalDateTime createdAt,

        @Schema(description = "Okundu mu?", example = "false")
        Boolean isRead
) {}