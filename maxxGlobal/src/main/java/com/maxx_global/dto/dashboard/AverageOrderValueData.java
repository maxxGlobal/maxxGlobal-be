package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Ortalama sipariş değeri verisi")
public record AverageOrderValueData(
        @Schema(description = "Ay-Yıl", example = "2024-01")
        String month,

        @Schema(description = "Ay adı", example = "Ocak 2024")
        String monthName,

        @Schema(description = "Ortalama sipariş değeri", example = "5890.25")
        BigDecimal averageOrderValue,

        @Schema(description = "Sipariş sayısı", example = "212")
        Long orderCount
) {}