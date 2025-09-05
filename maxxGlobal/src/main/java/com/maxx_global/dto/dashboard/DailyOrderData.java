package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Günlük sipariş verisi")
public record DailyOrderData(
        @Schema(description = "Tarih", example = "2024-01-15")
        LocalDate date,

        @Schema(description = "Gün adı", example = "Pazartesi")
        String dayName,

        @Schema(description = "Sipariş sayısı", example = "18")
        Long orderCount,

        @Schema(description = "Ciro", example = "125000.75")
        BigDecimal revenue
) {}