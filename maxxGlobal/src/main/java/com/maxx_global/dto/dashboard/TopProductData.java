package com.maxx_global.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Top ürün verisi")
public record TopProductData(
        @Schema(description = "Ürün ID", example = "1")
        Long productId,

        @Schema(description = "Ürün adı", example = "Titanyum İmplant 4.5mm")
        String productName,

        @Schema(description = "Ürün kodu", example = "TI-001")
        String productCode,

        @Schema(description = "Kategori adı", example = "Dental İmplantlar")
        String categoryName,

        @Schema(description = "Toplam sipariş miktarı", example = "450")
        Long totalQuantityOrdered,

        @Schema(description = "Sipariş sayısı", example = "89")
        Long orderCount,

        @Schema(description = "Toplam ciro", example = "675000.00")
        BigDecimal totalRevenue,

        @Schema(description = "Sıralama", example = "1")
        Integer rank
) {}
