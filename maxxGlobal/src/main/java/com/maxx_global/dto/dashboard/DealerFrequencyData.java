package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Bayi sıklık verisi")
public record DealerFrequencyData(
        @Schema(description = "Bayi ID", example = "1")
        Long dealerId,

        @Schema(description = "Bayi adı", example = "ABC Medikal Ltd.")
        String dealerName,

        @Schema(description = "Sipariş sayısı (X ekseni)", example = "145")
        Long orderCount,

        @Schema(description = "Ortalama sipariş değeri (Y ekseni)", example = "6793.45")
        BigDecimal averageOrderValue,

        @Schema(description = "Bayi büyüklüğü (bubble size)", example = "985000.50")
        BigDecimal totalRevenue,

        @Schema(description = "Sipariş sıklığı (gün)", example = "2.3")
        Double orderFrequencyDays
) {}