package com.maxx_global.dto.stock;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Ürün/Varyant stok özet bilgisi")
public record StockSummaryResponse(
        @Schema(description = "Ürün ID'si", example = "1")
        Long productId,

        @Schema(description = "Ürün adı", example = "Titanyum İmplant")
        String productName,

        @Schema(description = "Ürün kodu", example = "TI-001")
        String productCode,

        @Schema(description = "Varyant ID'si (varsa)", example = "5")
        Long variantId,

        @Schema(description = "Varyant SKU'su (varsa)", example = "TI-001-XL")
        String variantSku,

        @Schema(description = "Varyant görünen adı (varsa)", example = "Titanyum İmplant - XL")
        String variantDisplayName,

        @Schema(description = "Varyant boyutu (varsa)", example = "XL")
        String variantSize,

        @Schema(description = "Mevcut stok", example = "150")
        Integer currentStock,

        @Schema(description = "Toplam giriş", example = "500")
        Integer totalStockIn,

        @Schema(description = "Toplam çıkış", example = "350")
        Integer totalStockOut,

        @Schema(description = "Ortalama maliyet", example = "25.75")
        BigDecimal averageCost,

        @Schema(description = "Toplam değer", example = "3862.50")
        BigDecimal totalValue,

        @Schema(description = "Son hareket tarihi", example = "2024-01-15T10:30:00")
        LocalDateTime lastMovementDate,

        @Schema(description = "Son hareket tipi", example = "STOK GİRİŞİ")
        String lastMovementType
) {}