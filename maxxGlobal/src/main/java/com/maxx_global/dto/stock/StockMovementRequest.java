package com.maxx_global.dto.stock;

import com.maxx_global.enums.StockMovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "Stok hareketi oluşturma isteği")
public record StockMovementRequest(
        // ⚠️ DEPRECATED
        @Schema(description = "Ürün ID'si (DEPRECATED - productVariantId kullanın)", example = "1", deprecated = true)
        @Min(value = 1, message = "Ürün ID'si 1'den büyük olmalıdır")
        @Deprecated
        Long productId,

        // ✅ YENİ - Varyant ID
        @Schema(description = "Ürün Varyant ID'si", example = "1", required = true)
        @NotNull(message = "Ürün Varyant ID'si gereklidir")
        @Min(value = 1, message = "Ürün Varyant ID'si 1'den büyük olmalıdır")
        Long productVariantId,

        @Schema(description = "Hareket tipi", example = "STOCK_IN", required = true)
        @NotNull(message = "Hareket tipi gereklidir")
        StockMovementType movementType,

        @Schema(description = "Miktar", example = "100", required = true)
        @NotNull(message = "Miktar gereklidir")
        @Min(value = 1, message = "Miktar 1'den büyük olmalıdır")
        Integer quantity,

        @Schema(description = "Birim maliyet", example = "25.50")
        @DecimalMin(value = "0", message = "Birim maliyet negatif olamaz")
        @Digits(integer = 10, fraction = 2, message = "Geçersiz maliyet formatı")
        BigDecimal unitCost,

        @Schema(description = "Batch/Lot numarası", example = "BATCH-2024-001")
        @Size(max = 100, message = "Batch numarası 100 karakteri geçemez")
        String batchNumber,

        @Schema(description = "Son kullanma tarihi", example = "2027-01-15")
        LocalDate expiryDate,

        @Schema(description = "Referans tipi", example = "MANUAL")
        @Size(max = 50, message = "Referans tipi 50 karakteri geçemez")
        String referenceType,

        @Schema(description = "Referans ID'si", example = "123")
        Long referenceId,

        @Schema(description = "Doküman numarası", example = "DOC-2024-001")
        @Size(max = 100, message = "Doküman numarası 100 karakteri geçemez")
        String documentNumber,

        @Schema(description = "Notlar", example = "Yeni stok girişi yapıldı")
        @Size(max = 1000, message = "Notlar 1000 karakteri geçemez")
        String notes
) {
    public BigDecimal getTotalCost() {
        if (unitCost != null && quantity != null) {
            return unitCost.multiply(BigDecimal.valueOf(quantity));
        }
        return null;
    }
}