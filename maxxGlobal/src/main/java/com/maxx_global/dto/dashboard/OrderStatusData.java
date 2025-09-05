package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Sipariş durum verisi")
public record OrderStatusData(
        @Schema(description = "Durum adı", example = "Tamamlandı")
        String statusName,

        @Schema(description = "Durum kodu", example = "COMPLETED")
        String statusCode,

        @Schema(description = "Sipariş sayısı", example = "980")
        Long count,

        @Schema(description = "Yüzde", example = "78.4")
        Double percentage,

        @Schema(description = "Renk kodu", example = "#28a745")
        String color
) {}