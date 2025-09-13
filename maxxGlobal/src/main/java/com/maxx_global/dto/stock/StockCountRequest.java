package com.maxx_global.dto.stock;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Stok sayım isteği")
public record StockCountRequest(
        @Schema(description = "Ürün ID'si", example = "1", required = true)
        @NotNull(message = "Ürün ID'si gereklidir")
        @Min(value = 1, message = "Ürün ID'si 1'den büyük olmalıdır")
        Long productId,

        @Schema(description = "Sayılan miktar", example = "95", required = true)
        @NotNull(message = "Sayılan miktar gereklidir")
        @Min(value = 0, message = "Sayılan miktar negatif olamaz")
        Integer countedQuantity,

        @Schema(description = "Sayım doküman numarası", example = "COUNT-2024-001")
        @Size(max = 100, message = "Doküman numarası 100 karakteri geçemez")
        String documentNumber,

        @Schema(description = "Sayım notları", example = "Aylık stok sayımı")
        @Size(max = 500, message = "Notlar 500 karakteri geçemez")
        String notes
) {}
