package com.maxx_global.dto.productPriceExcell;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Excel fiyat import sonucu")
public record PriceImportResult(
        @Schema(description = "Toplam işlenen satır sayısı", example = "150")
        Integer totalRows,

        @Schema(description = "Başarılı işlenen satır sayısı", example = "145")
        Integer successCount,

        @Schema(description = "Hatalı satır sayısı", example = "5")
        Integer failedCount,

        @Schema(description = "Güncellenen fiyat sayısı", example = "50")
        Integer updatedCount,

        @Schema(description = "Yeni eklenen fiyat sayısı", example = "95")
        Integer createdCount,

        @Schema(description = "Hata detayları")
        List<PriceImportError> errors,

        @Schema(description = "İşlem başarılı mı?", example = "true")
        Boolean success,

        @Schema(description = "İşlem mesajı")
        String message
) {}