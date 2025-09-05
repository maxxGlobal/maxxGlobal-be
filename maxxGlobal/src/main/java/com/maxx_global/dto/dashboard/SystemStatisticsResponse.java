package com.maxx_global.dto.dashboard;

import com.maxx_global.dto.product.ProductStatistics;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Sistem istatistikleri")
public record SystemStatisticsResponse(
        @Schema(description = "Kullanıcı istatistikleri")
        UserStatistics userStats,

        @Schema(description = "Bayi istatistikleri")
        DealerStatistics dealerStats,

        @Schema(description = "Ürün istatistikleri")
        ProductStatistics productStats,

        @Schema(description = "Sipariş istatistikleri")
        OrderStatistics orderStats
) {}