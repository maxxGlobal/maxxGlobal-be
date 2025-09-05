package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kullanıcı istatistikleri")
public record UserStatistics(
        @Schema(description = "Toplam kullanıcı", example = "150")
        Long total,

        @Schema(description = "Aktif kullanıcı", example = "142")
        Long active,

        @Schema(description = "Pasif kullanıcı", example = "8")
        Long inactive,

        @Schema(description = "Bu ay yeni kullanıcı", example = "12")
        Long newThisMonth
) {}