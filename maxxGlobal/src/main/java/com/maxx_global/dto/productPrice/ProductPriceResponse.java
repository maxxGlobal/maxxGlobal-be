package com.maxx_global.dto.productPrice;

import com.maxx_global.enums.CurrencyType;
import com.maxx_global.enums.PriceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Ürün fiyatı yanıt modeli")
public record ProductPriceResponse(

        @Schema(description = "Fiyat ID'si", example = "1")
        Long id,

        @Schema(description = "Ürün ID'si", example = "1")
        Long productId,

        @Schema(description = "Ürün adı", example = "Titanyum İmplant")
        String productName,

        @Schema(description = "Ürün kodu", example = "TI-001")
        String productCode,

        @Schema(description = "Bayi ID'si", example = "1")
        Long dealerId,

        @Schema(description = "Bayi adı", example = "ABC Medikal Ltd.")
        String dealerName,

        @Schema(description = "Para birimi", example = "TRY")
        CurrencyType currency,

        @Schema(description = "Fiyat tipi", example = "PRICE_1")
        PriceType priceType,

        @Schema(description = "Fiyat miktarı", example = "150.75")
        BigDecimal amount,

        @Schema(description = "Geçerlilik başlangıç tarihi", example = "2025-01-01T00:00:00")
        LocalDateTime validFrom,

        @Schema(description = "Geçerlilik bitiş tarihi", example = "2025-12-31T23:59:59")
        LocalDateTime validUntil,

        @Schema(description = "Fiyat aktif mi?", example = "true")
        Boolean isActive,

        @Schema(description = "Şu an geçerli mi?", example = "true")
        Boolean isValidNow,

        @Schema(description = "Oluşturulma tarihi", example = "2025-01-01T10:30:00")
        LocalDateTime createdDate,

        @Schema(description = "Güncellenme tarihi", example = "2025-01-01T10:30:00")
        LocalDateTime updatedDate,

        @Schema(description = "Durum", example = "ACTIVE")
        String status
){}