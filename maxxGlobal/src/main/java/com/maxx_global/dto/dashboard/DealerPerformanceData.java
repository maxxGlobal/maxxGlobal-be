package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Bayi performans verisi")
public record DealerPerformanceData(
        @Schema(description = "Bayi ID", example = "1")
        Long dealerId,

        @Schema(description = "Bayi adı", example = "ABC Medikal Ltd.")
        String dealerName,

        @Schema(description = "Toplam ciro", example = "985000.50")
        BigDecimal totalRevenue,

        @Schema(description = "Sipariş sayısı", example = "145")
        Long orderCount,

        @Schema(description = "Tamamlanma oranı (%)", example = "94.5")
        Double completionRate,

        @Schema(description = "Ortalama sipariş değeri", example = "6793.45")
        BigDecimal averageOrderValue,

        @Schema(description = "Performans skoru", example = "8.7")
        Double performanceScore
) {}