package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Bayi istatistikleri")
public record DealerStatistics(
        @Schema(description = "Toplam bayi", example = "45")
        Long total,

        @Schema(description = "Aktif bayi", example = "42")
        Long active,

        @Schema(description = "Bu ay yeni bayi", example = "3")
        Long newThisMonth
) {}