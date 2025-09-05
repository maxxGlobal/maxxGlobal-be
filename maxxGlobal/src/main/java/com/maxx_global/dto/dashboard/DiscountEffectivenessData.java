package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "İndirim etkinlik verisi")
public record DiscountEffectivenessData(
        @Schema(description = "İndirim ID", example = "1")
        Long discountId,

        @Schema(description = "İndirim adı", example = "Kış Kampanyası 2024")
        String discountName,

        @Schema(description = "İndirim tipi", example = "PERCENTAGE")
        String discountType,

        @Schema(description = "İndirim değeri", example = "15.0")
        BigDecimal discountValue,

        @Schema(description = "Kullanım sayısı", example = "234")
        Long usageCount,

        @Schema(description = "Toplam tasarruf", example = "125000.50")
        BigDecimal totalSavings,

        @Schema(description = "Etkilenen sipariş sayısı", example = "234")
        Long affectedOrders,

        @Schema(description = "Etkinlik skoru", example = "8.5")
        Double effectivenessScore
) {}