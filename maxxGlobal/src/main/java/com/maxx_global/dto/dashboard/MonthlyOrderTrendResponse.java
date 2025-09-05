package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Aylık sipariş trendi")
public record MonthlyOrderTrendResponse(
        @Schema(description = "Tarih aralığı")
        String period,

        @Schema(description = "Aylık veriler")
        List<MonthlyOrderData> monthlyData,

        @Schema(description = "Trend yönü (UP/DOWN/STABLE)", example = "UP")
        String trendDirection,

        @Schema(description = "Ortalama aylık sipariş", example = "285.5")
        Double averageMonthlyOrders
) {}