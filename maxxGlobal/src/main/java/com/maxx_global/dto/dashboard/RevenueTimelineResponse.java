package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Gelir trendi")
public record RevenueTimelineResponse(
        @Schema(description = "Tarih aralığı")
        String period,

        @Schema(description = "Aylık gelir verileri")
        List<RevenueData> monthlyRevenue,

        @Schema(description = "Toplam gelir", example = "15650000.75")
        BigDecimal totalRevenue,

        @Schema(description = "Ortalama aylık gelir", example = "1304166.73")
        BigDecimal averageMonthlyRevenue,

        @Schema(description = "Büyüme oranı (%)", example = "12.5")
        Double growthRate
) {}