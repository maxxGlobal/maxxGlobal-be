package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Aylık sipariş verisi")
public record MonthlyOrderData(
        @Schema(description = "Ay-Yıl", example = "2024-01")
        String month,

        @Schema(description = "Ay adı", example = "Ocak 2024")
        String monthName,

        @Schema(description = "Sipariş sayısı", example = "245")
        Long orderCount,

        @Schema(description = "Ciro", example = "1650000.50")
        BigDecimal revenue
) {}