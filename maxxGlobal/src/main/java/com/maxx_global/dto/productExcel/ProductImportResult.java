package com.maxx_global.dto.productExcel;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Ürün Excel import sonucu")
public record ProductImportResult(
        @Schema(description = "Toplam işlenen satır sayısı", example = "150")
        Integer totalRows,

        @Schema(description = "Başarılı işlenen satır sayısı", example = "145")
        Integer successCount,

        @Schema(description = "Hatalı satır sayısı", example = "5")
        Integer failedCount,

        @Schema(description = "Güncellenen ürün sayısı", example = "50")
        Integer updatedCount,

        @Schema(description = "Yeni eklenen ürün sayısı", example = "95")
        Integer createdCount,

        @Schema(description = "Hata detayları")
        List<ProductImportError> errors,

        @Schema(description = "İşlem başarılı mı?", example = "true")
        Boolean success,

        @Schema(description = "İşlem mesajı")
        String message
) {}