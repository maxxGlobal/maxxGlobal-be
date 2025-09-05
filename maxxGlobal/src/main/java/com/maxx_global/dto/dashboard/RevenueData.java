package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Gelir verisi")
public record RevenueData(
        @Schema(description = "Ay-Yıl", example = "2024-01")
        String month,

        @Schema(description = "Ay adı", example = "Ocak 2024")
        String monthName,

        @Schema(description = "Gelir", example = "1250000.50")
        BigDecimal revenue,

        @Schema(description = "Önceki aya göre değişim (%)", example = "8.5")
        Double changePercentage
) {}
