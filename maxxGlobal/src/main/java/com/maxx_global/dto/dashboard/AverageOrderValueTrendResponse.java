package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Ortalama sipariş değeri trendi")
public record AverageOrderValueTrendResponse(
        @Schema(description = "Tarih aralığı")
        String period,

        @Schema(description = "Aylık AOV verileri")
        List<AverageOrderValueData> monthlyAOV,

        @Schema(description = "Genel ortalama AOV", example = "6250.75")
        BigDecimal overallAverageOrderValue,

        @Schema(description = "Trend yönü", example = "UP")
        String trendDirection
) {}