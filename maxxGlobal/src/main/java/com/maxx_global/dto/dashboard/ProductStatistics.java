package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ürün istatistikleri")
public record ProductStatistics(
        @Schema(description = "Toplam ürün", example = "1250")
        Long total,

        @Schema(description = "Stokta olan", example = "1185")
        Long inStock,

        @Schema(description = "Stokta olmayan", example = "65")
        Long outOfStock,

        @Schema(description = "Süresi dolan", example = "15")
        Long expired,

        @Schema(description = "Resmi olmayan", example = "23")
        Long withoutImages
) {}