package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Sipariş istatistikleri")
public record OrderStatistics(
        @Schema(description = "Toplam sipariş", example = "3420")
        Long total,

        @Schema(description = "Bekleyen", example = "23")
        Long pending,

        @Schema(description = "Onaylanan", example = "156")
        Long approved,

        @Schema(description = "Tamamlanan", example = "3180")
        Long completed,

        @Schema(description = "İptal edilen", example = "61")
        Long cancelled,

        @Schema(description = "Tamamlanan", example = "3180")
        Long shipped,

        @Schema(description = "İptal edilen", example = "61")
        Long rejected,

        @Schema(description = "İptal edilen", example = "61")
        Long edited,

        @Schema(description = "Bu ay toplam", example = "187")
        Long thisMonth,

        @Schema(description = "Toplam ciro", example = "12500000.75")
        BigDecimal totalRevenue
) {}